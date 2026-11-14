package com.muzin.mu.zin.service.support;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.notification.NotificationType;
import com.muzin.mu.zin.entity.support.SupportInquiry;
import com.muzin.mu.zin.event.NotificationEvent;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.repository.UserRoleRepository;
import com.muzin.mu.zin.repository.support.SupportInquiryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 운영자 문의: 하루 제한, 관리자 알림, 답변 한 번 (DB 없이)
class SupportServiceTest {

    private SupportInquiryRepository supportRepository;
    private UserRoleRepository userRoleRepository;
    private ApplicationEventPublisher publisher;
    private SupportService service;

    private final User user = User.builder().userId(1L).email("u@x.com").password("x").username("유저").build();
    private final User admin = User.builder().userId(9L).email("a@x.com").password("x").username("관리자").build();

    @BeforeEach
    void setUp() {
        supportRepository = mock(SupportInquiryRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        userRoleRepository = mock(UserRoleRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new SupportService(supportRepository, userRepository, userRoleRepository, publisher);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(supportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("문의하면 관리자 전원에게 알림이 가고, 보낸 사람이 관리자여도 자기 자신에게는 가지 않는다")
    void notifiesAdmins() {
        when(userRoleRepository.findActiveAdminUserIds()).thenReturn(List.of(9L, 1L, 10L));

        service.create(1L, "BUG", "  화면이 안 열려요  ");

        ArgumentCaptor<NotificationEvent> ev = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(publisher, times(2)).publishEvent(ev.capture());
        assertThat(ev.getAllValues()).extracting(NotificationEvent::recipientUserId).containsExactly(9L, 10L);
        assertThat(ev.getAllValues()).allMatch(e -> e.type() == NotificationType.SUPPORT_CREATED);
    }

    @Test
    @DisplayName("하루 제한을 넘으면 저장하지 않는다")
    void dailyLimit() {
        when(supportRepository.countByUser_UserIdAndCreateDtGreaterThanEqual(eq(1L), any())).thenReturn((long) SupportService.DAILY_LIMIT);

        assertThatThrownBy(() -> service.create(1L, "ETC", "문의")).isInstanceOf(ApiException.class);
        verify(supportRepository, never()).save(any());
    }

    @Test
    @DisplayName("보관 기간 정리는 6개월 전을 기준으로 지운다")
    void retentionCutoff() {
        service.deleteExpired();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(supportRepository).deleteExpired(cutoff.capture());
        LocalDateTime expected = TimeDefaults.nowKst().minusMonths(SupportService.RETENTION_MONTHS);
        assertThat(Duration.between(cutoff.getValue(), expected).abs()).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("없는 유형은 거절한다")
    void badCategory() {
        assertThatThrownBy(() -> service.create(1L, "PAYMENT", "문의")).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("답변하면 문의한 사람에게 알림이 가고, 이미 답변한 문의는 다시 답변할 수 없다")
    void replyOnce() {
        SupportInquiry inquiry = SupportInquiry.builder().supportId(5L).user(user)
                .category(SupportInquiry.Category.ACCOUNT).content("로그인이 안 돼요").build();
        when(supportRepository.findById(5L)).thenReturn(Optional.of(inquiry));

        service.reply(9L, 5L, "확인했어요");

        assertThat(inquiry.getStatus()).isEqualTo(SupportInquiry.Status.ANSWERED);
        ArgumentCaptor<NotificationEvent> ev = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().recipientUserId()).isEqualTo(1L);
        assertThat(ev.getValue().type()).isEqualTo(NotificationType.SUPPORT_REPLIED);

        assertThatThrownBy(() -> service.reply(9L, 5L, "또 답변")).isInstanceOf(ApiException.class);
    }
}
