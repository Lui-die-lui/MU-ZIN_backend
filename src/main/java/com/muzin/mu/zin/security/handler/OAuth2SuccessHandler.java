package com.muzin.mu.zin.security.handler;

import com.muzin.mu.zin.entity.OAuth2UserEntity;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.repository.OAuth2UserRepository;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.security.jwt.JwtUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

// 현재 merge 없이 signup 자동 + signin
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final OAuth2UserRepository oAuth2UserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${oauth2.redirect.success-url}")
    private String successRedirectUrl;

    @Value("${oauth2.redirect.error-url}")
    private String errorRedirectUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        // loadUser() 가 반환한 OAuth2User 객체가 principal로 들어감
        DefaultOAuth2User principal = (DefaultOAuth2User) authentication.getPrincipal();

        String provider = principal.getAttribute("provider");
        String providerUserId = principal.getAttribute("providerUserId");
        String email = principal.getAttribute("email");

        if (provider == null || providerUserId == null) {
            redirectError(request, response, "OAUTH2_MISSING");
            return;
        }

        // Signin : 이미 연동된 소셜이면 바로 로그인
        Optional<OAuth2UserEntity> linked = oAuth2UserRepository.findByProviderAndProviderUserId(provider, providerUserId);

        if (linked.isPresent()) {
            redirectWithToken(request, response, linked.get().getUser());
            return;
        }

        // email 없으면 Signup 불가(카카오 미동의 등)
        if (email == null || email.isBlank()) {
            redirectError(request, response, "EMAIL_REQUIRED");
            return;
        }

        // Merge 미지원 MVP - 기존 이메일 계정 있으면 가입 불가 (고려해볼것 동일한 이메일 가입 안되는거 서비스 상으로 좀 그럼)
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {

//            redirectError(request, response, "MERGE_REQUIRED");
            User user = existing.get();

            if (oAuth2UserRepository.existsByUserAndProvider(user, provider)) {
                // 이미 같은 provider로 연동되어 있으면 중복 저장하지 말고 로그인만
                redirectWithToken(request, response, user);
                return;
            }

            oAuth2UserRepository.save(
                    OAuth2UserEntity.builder()
                            .user(user)
                            .provider(provider)
                            .providerUserId(providerUserId)
                            .build()
            );


            redirectWithToken(request, response, user);
            return;
        }

        // Signup - 새로운 User 생성 + OAuth2 유저 생성
        User newUser = User.builder()
                .email(email)
                .username(email)
                .password(passwordEncoder.encode("oauth2-" + UUID.randomUUID()))
                .build();

        userRepository.save(newUser);

        oAuth2UserRepository.save(
                OAuth2UserEntity.builder()
                        .user(newUser)
                        .provider(provider)
                        .providerUserId(providerUserId)
                        .build()
        );

        redirectWithToken(request, response, newUser);
    }

    // 성공 시 담아줄 토큰
    private void redirectWithToken(HttpServletRequest request,
                                   HttpServletResponse response,
                                   User user) throws IOException {

        String accessToken = jwtUtils.generateAccessToken(user.getUserId());

        String targetUrl = UriComponentsBuilder.fromUriString(successRedirectUrl)
                .queryParam("accessToken", accessToken)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        getRedirectStrategy().sendRedirect(request,response, targetUrl);
    }

    // 리다이렉트 실패 시
    private void redirectError(HttpServletRequest request,
                               HttpServletResponse response,
                               String errorCode) throws IOException {

        String targetUrl = UriComponentsBuilder.fromUriString(errorRedirectUrl)
                .queryParam("errorCode", errorCode)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
