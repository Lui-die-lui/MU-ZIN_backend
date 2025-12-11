package com.muzin.mu.zin.service;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.mail.SendMailRequest;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.UserRole;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.repository.UserRoleRepository;
import com.muzin.mu.zin.security.jwt.JwtUtils;
import com.muzin.mu.zin.security.model.PrincipalUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender javaMailSender;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;


    public ApiRespDto<?> sendMail(SendMailRequest sendMailRequest, PrincipalUser principalUser) {
        // 요청한 이메일이 본인 이메일이 맞는지 체크
        if (!principalUser.getEmail().equals(sendMailRequest.email())) {
            return new ApiRespDto<>("failed", "잘못된 요청 입니다.", null);
        }

        // 유저 조회
        Optional<User> optionalUser = userRepository.findByEmail(sendMailRequest.email());
        if (optionalUser.isEmpty()) {
            return new ApiRespDto<>("failed","존재하지 않는 이메일입니다.",null);
        }

        User user = optionalUser.get();

        // 이미 인증 완료된 계정이면 재발송 막기
        // 웹개발 수업이랑 다른 점 - RoleId로 관리하는게 아닌 인증 받았나 - 안 받았나(EmailVerified) 차이
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return new ApiRespDto<>("failed", "이미 이메일 인증이 완료된 계정입니다.", null);
        }

        // 인증 토큰 발급
        String verifyToken = jwtUtils.generateVerifyToken(user.getUserId().toString());

        //메일 전송
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("이메일 인증입니다.");
        message.setText("링크를 클릭하여 인증을 완료해주세요 : " +
                "http://localhost:8080/mail/verify?verifyToken=" + verifyToken);

        javaMailSender.send(message);

        return new ApiRespDto<>("success", "이메일 전송이 완료되었습니다.", null);
    }

    @Transactional
    // 메일 링크 눌렀을 때
    public Map<String, Object> verify(String token) {
        // 컨트롤러에 리턴해줄 응답을 담는 맵
//        Map<String, Object> resultMap = null;

        try {
            // 넘어온 verifyToken(쿼리 파라미터)에서 payload를 파싱
            // 서명/유효기간 등 체크하면서 디코딩해서 Claims 객체로 반환
            // sub, jti, exp 같은 값을 꺼낼 수 있는 정보(payload)를 담는 객체
            Claims claims = jwtUtils.getClaims(token);

            // JWT payload의 sub 값
            if (!"VerifyToken".equals(claims.getSubject())) {
                return Map.of("status", "failed", "message", "잘못된 요청입니다.");
            }

            // userId 꺼내기
            Integer userId = Integer.parseInt(claims.getId());

            // 유저 조회
            User user = userRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자 입니다."));

            // 이미 인증된 경우
            if (Boolean.TRUE.equals(user.getEmailVerified())) {
                return Map.of("status", "failed", "message", "이미 인증 완료된 메일입니다.");
            }

            // 아직 인증이 안 된 경우 (이메일 인증 완료 처리)
            user.setEmailVerified(true); // 엔티티 필드만 바꿔주면 됨
//          userRepository.save(user); // 나중에 이거 @Transactional

            return Map.of("status", "success", "message", "이메일 인증이 완료되었습니다.");

        // 토큰 만료됐을 때
        } catch (ExpiredJwtException e) {
            return Map.of("status", "failed", "message",
                    "인증 시간이 만료된 요청입니다. \n인증 메일을 다시 요청하세요.");
        } catch (Exception e) {
            return Map.of("status", "failed", "message",
                    "잘못된 요청입니다.\n인증 메일을 다시 요청하세요.");
        }
    }
}
