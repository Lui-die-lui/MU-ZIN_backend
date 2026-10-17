package com.muzin.mu.zin.service;

import com.muzin.mu.zin.dto.mail.SendMailRequest;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.security.jwt.JwtUtils;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.security.ratelimit.VerifyMailCooldown;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// 실제 메일은 보내지 않고(가짜 발송기) 발송 횟수 제한만 검증
class MailServiceCooldownTest {

    private JavaMailSender mailSender;
    private MailService mailService;
    private final PrincipalUser principal = PrincipalUser.builder().userId(1L).email("a@x.com").build();

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(ignored -> new MimeMessage(Session.getInstance(new Properties())));
        UserRepository userRepository = mock(UserRepository.class);
        User user = User.builder().userId(1L).email("a@x.com").password("x").username("a").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String secret = Base64.getEncoder().encodeToString(new byte[64]);
        mailService = new MailService(mailSender, userRepository, new JwtUtils(secret), new VerifyMailCooldown());
        ReflectionTestUtils.setField(mailService, "backendUrl", "http://localhost:8080");
    }

    @Test
    @DisplayName("연속으로 눌러도 메일은 1통만 발송되고 두 번째는 429")
    void secondSendBlocked() {
        mailService.sendMail(new SendMailRequest("a@x.com"), principal);

        assertThatThrownBy(() -> mailService.sendMail(new SendMailRequest("a@x.com"), principal))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("MAIL_COOLDOWN"));

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("HTML 메일에 로고, 3분 안내, 인증 버튼을 표시")
    void mailContainsLogoAndThreeMinuteVerificationButton() throws Exception {
        mailService.sendMail(new SendMailRequest("a@x.com"), principal);

        var captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        captor.getValue().saveChanges();
        assertThat(captor.getValue().getContentType()).containsIgnoringCase("multipart/mixed");

        String html = MailService.buildHtml("http://localhost:8080/mail/verify?verifyToken=test-token");
        assertThat(html)
                .contains("MU:ZIN", "3분", "이메일 인증하기", "/mail/verify?verifyToken=test-token")
                .doesNotContain("#7c3aed", "#a78bfa", "linear-gradient", "box-shadow", "100%%");
    }

    @Test
    @DisplayName("메일 서버 오류로 실패하면 대기 없이 바로 재시도 가능")
    void failureDoesNotStartCooldown() {
        doThrow(new MailSendException("smtp down")).doNothing().when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> mailService.sendMail(new SendMailRequest("a@x.com"), principal))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("MAIL_SEND_FAILED"));

        mailService.sendMail(new SendMailRequest("a@x.com"), principal); // 바로 재시도 성공
        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }
}
