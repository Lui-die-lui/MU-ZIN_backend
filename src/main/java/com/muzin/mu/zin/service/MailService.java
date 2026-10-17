package com.muzin.mu.zin.service;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.mail.SendMailRequest;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.security.jwt.JwtUtils;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.security.ratelimit.VerifyMailCooldown;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    // 인증 링크 결과 코드 (프론트 /mail/verify-result 에서 문구로 변환)
    public static final String VERIFIED = "VERIFIED";
    public static final String ALREADY_VERIFIED = "ALREADY_VERIFIED";
    public static final String EXPIRED = "EXPIRED";
    public static final String INVALID = "INVALID";

    private final JavaMailSender javaMailSender;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final VerifyMailCooldown verifyMailCooldown;

    @Value("${app.backend-url}")
    private String backendUrl;

    public ApiRespDto<?> sendMail(SendMailRequest sendMailRequest, PrincipalUser principalUser) {
        // 요청한 이메일이 본인 이메일이 맞는지 체크
        if (sendMailRequest == null || !principalUser.getEmail().equalsIgnoreCase(sendMailRequest.email())) {
            throw ApiException.badRequest("본인 계정의 이메일로만 인증 메일을 받을 수 있습니다.");
        }

        User user = userRepository.findById(principalUser.getUserId())
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 사용자입니다."));

        // 이미 인증 완료된 계정이면 재발송 막기
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw ApiException.conflict("ALREADY_VERIFIED", "이미 이메일 인증이 완료된 계정입니다.");
        }

        // 연속 발송 방지: 마지막 발송 후 3분 동안은 429 (인증 링크 유효시간과 동일)
        Instant acquiredAt = verifyMailCooldown.acquire(user.getUserId());

        // 인증 토큰 발급 (3분 유효)
        String verifyToken = jwtUtils.generateVerifyToken(user.getUserId().toString());
        String link = UriComponentsBuilder.fromUriString(backendUrl)
                .path("/mail/verify")
                .queryParam("verifyToken", verifyToken)
                .toUriString();

        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setSubject("[MU:ZIN] 3분 안에 이메일 인증을 완료해주세요");
            helper.setText(buildPlainText(link), buildHtml(link));
            javaMailSender.send(message);
        } catch (MailException | MessagingException e) {
            // 발송 자체가 실패했으면 대기 시간을 걸지 않음 (바로 다시 시도 가능)
            verifyMailCooldown.release(user.getUserId(), acquiredAt);
            log.error("인증 메일 발송 실패", e);
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "MAIL_SEND_FAILED",
                    "메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        return new ApiRespDto<>("success", "인증 메일을 보냈습니다. 3분 안에 메일의 링크를 눌러 인증해주세요.", null);
    }

    static String buildPlainText(String link) {
        return "MU:ZIN 이메일 인증\n\n"
                + "아래 링크를 3분 안에 열어 이메일 인증을 완료해주세요.\n"
                + "3분이 지나면 링크가 만료되며, 마이페이지에서 인증 메일을 다시 요청할 수 있습니다.\n\n"
                + link + "\n\n"
                + "본인이 요청하지 않았다면 이 메일을 무시해주세요.";
    }

    static String buildHtml(String link) {
        String safeLink = escapeHtml(link);
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>MU:ZIN 이메일 인증</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f3f4f6;color:#111827;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Noto Sans KR',Arial,sans-serif;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">MU:ZIN 이메일 인증을 3분 안에 완료해주세요.</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;background-color:#f3f4f6;">
                    <tr>
                      <td align="center" style="padding:32px 12px;">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;max-width:560px;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:12px;">
                          <tr>
                            <td align="left" style="padding:24px 28px;border-bottom:1px solid #e5e7eb;">
                              <div style="font-size:21px;line-height:1.2;font-weight:800;letter-spacing:0.8px;color:#111827;">MU:ZIN</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:36px 28px 32px;">
                              <p style="margin:0 0 12px;font-size:13px;line-height:1.5;font-weight:700;letter-spacing:0.2px;color:#6b7280;">이메일 인증</p>
                              <h1 style="margin:0 0 16px;font-size:25px;line-height:1.4;font-weight:800;color:#111827;">이메일 주소를 확인해주세요</h1>
                              <p style="margin:0 0 24px;font-size:15px;line-height:1.75;color:#374151;">MU:ZIN에서 안전하게 아티스트를 만나고 레슨을 이용할 수 있도록 이메일 주소를 인증해주세요.</p>
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;margin:0 0 24px;background-color:#f3f4f6;border-radius:8px;">
                                <tr>
                                  <td style="padding:14px 16px;font-size:14px;line-height:1.6;font-weight:700;color:#374151;">보안을 위해 인증 링크는 3분 후 만료됩니다.</td>
                                </tr>
                              </table>
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                                <tr>
                                  <td align="center" bgcolor="#111827" style="background-color:#111827;border-radius:8px;">
                                    <a href="%s" target="_blank" style="display:block;padding:14px 20px;border:1px solid #111827;border-radius:8px;color:#ffffff;text-decoration:none;font-size:15px;line-height:1.4;font-weight:700;text-align:center;">이메일 인증하기</a>
                                  </td>
                                </tr>
                              </table>
                              <div style="height:28px;line-height:28px;">&nbsp;</div>
                              <p style="margin:0 0 10px;font-size:13px;line-height:1.65;color:#6b7280;">버튼이 열리지 않으면 아래 주소를 복사해 브라우저 주소창에 붙여넣어 주세요.</p>
                              <div style="padding:12px 14px;background-color:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;word-break:break-all;overflow-wrap:anywhere;font-size:12px;line-height:1.65;color:#374151;">
                                <a href="%s" style="color:#374151;text-decoration:underline;word-break:break-all;overflow-wrap:anywhere;">%s</a>
                              </div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:20px 28px;background-color:#f9fafb;border-top:1px solid #e5e7eb;border-radius:0 0 12px 12px;font-size:12px;line-height:1.7;color:#6b7280;">직접 요청하지 않은 인증 메일이라면 별도의 조치 없이 이 메일을 닫아주세요.<br>본 메일은 발신 전용입니다.</td>
                          </tr>
                        </table>
                        <p style="margin:18px 0 0;font-size:11px;line-height:1.6;color:#9ca3af;">MU:ZIN · 아티스트와 레슨을 잇는 공간</p>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(safeLink, safeLink, safeLink);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // 메일 링크 눌렀을 때 - 결과 코드 반환 (컨트롤러가 프론트 결과 페이지로 리다이렉트)
    @Transactional
    public String verify(String token) {
        try {
            // 서명/유효기간 체크 + payload 파싱
            Claims claims = jwtUtils.getClaims(token);

            // 액세스 토큰 등 다른 용도의 토큰은 거부
            if (!"VerifyToken".equals(claims.getSubject()) || claims.getId() == null) {
                return INVALID;
            }

            Optional<User> optionalUser = userRepository.findByUserId(Long.parseLong(claims.getId()));
            if (optionalUser.isEmpty()) {
                return INVALID;
            }

            User user = optionalUser.get();
            if (Boolean.TRUE.equals(user.getEmailVerified())) {
                return ALREADY_VERIFIED;
            }

            user.setEmailVerified(true); // 더티 체킹으로 저장
            return VERIFIED;

        } catch (ExpiredJwtException e) {
            return EXPIRED;
        } catch (JwtException | IllegalArgumentException e) {
            return INVALID;
        }
    }
}
