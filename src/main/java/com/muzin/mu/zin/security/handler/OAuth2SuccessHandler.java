package com.muzin.mu.zin.security.handler;

import com.muzin.mu.zin.entity.OAuth2UserEntity;
import com.muzin.mu.zin.entity.Role;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.UserRole;
import com.muzin.mu.zin.repository.OAuth2UserRepository;
import com.muzin.mu.zin.repository.RoleRepository;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.repository.UserRoleRepository;
import com.muzin.mu.zin.security.jwt.JwtUtils;
import com.muzin.mu.zin.service.UsernameGenerator;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 * 소셜 로그인 성공 후 처리
 *  - 이미 연동된 소셜 계정이면 로그인
 *  - 같은 이메일로 가입된 계정이 이미 있으면 (일반 가입이든 다른 소셜이든) 가입 거부: EMAIL_ALREADY_REGISTERED
 *    -> 이메일 1개당 계정 1개. 자동 연동(merge)은 하지 않음
 *       (제공자가 이메일 소유를 보장하지 않으면 남의 계정에 붙을 수 있어서)
 *  - 없으면 새 계정 생성 + 소셜 연동
 *
 * TODO(보류): 소셜 가입 유저의 이메일 인증 처리 방식 결정
 *  - 제공자의 이메일 인증 여부(Google email_verified, Kakao is_email_verified)를 믿고 바로 인증 처리할지
 *  - 일반 가입처럼 인증 메일을 따로 보낼지
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final OAuth2UserRepository oAuth2UserRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final UsernameGenerator usernameGenerator;

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
            redirectError(request, response, "OAUTH2_MISSING", null);
            return;
        }

        // Signin : 이미 연동된 소셜이면 바로 로그인
        Optional<OAuth2UserEntity> linked = oAuth2UserRepository.findByProviderAndProviderUserId(provider, providerUserId);
        if (linked.isPresent()) {
            User user = linked.get().getUser();
            if (user.isSuspended()) {
                redirectError(request, response, "ACCOUNT_SUSPENDED", null);
                return;
            }
            ensureUserRole(user);
            redirectWithToken(request, response, user);
            return;
        }

        // email 없으면 Signup 불가(카카오 미동의 등)
        if (email == null || email.isBlank()) {
            redirectError(request, response, "EMAIL_REQUIRED", null);
            return;
        }

        // 중복 가입 방지: 같은 이메일 계정이 이미 있으면 거부하고 기존 가입 방식을 알려줌
        Optional<User> existing = userRepository.findByEmailIgnoreCase(email.trim());
        if (existing.isPresent()) {
            List<OAuth2UserEntity> links = oAuth2UserRepository.findAllByUser_UserId(existing.get().getUserId());
            String registeredWith = links.isEmpty() ? "email" : links.get(0).getProvider();
            redirectError(request, response, "EMAIL_ALREADY_REGISTERED", registeredWith);
            return;
        }

        // Signup - 새로운 User 생성 + OAuth2 유저 생성 (비밀번호 로그인은 불가능한 랜덤값)
        User newUser = User.builder()
                .email(email.trim())
                .username(usernameGenerator.generate(email.trim())) // 공개 화면에 이메일이 노출되지 않게
                .password(passwordEncoder.encode("oauth2-" + UUID.randomUUID()))
                .build();

        userRepository.save(newUser);
        ensureUserRole(newUser);

        oAuth2UserRepository.save(
                OAuth2UserEntity.builder()
                        .user(newUser)
                        .provider(provider)
                        .providerUserId(providerUserId)
                        .build()
        );

        redirectWithToken(request, response, newUser);
    }

    // 가입 시 userRole (USER)를 보장
    private void ensureUserRole(User user) {

        // 이미 role 있으면 스킵
        if (userRoleRepository.existsByUser_UserId(user.getUserId())) {
            return;
        }

        Role userRoleEntity = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("USER Role not found"));

        // userRole을 붙여서 save 해줌
        userRoleRepository.save(
                UserRole.builder()
                        .user(user)
                        .role(userRoleEntity)
                        .build()
        );
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

    // 실패 시 프론트 /oauth2/redirect?errorCode=...(&provider=...)
    private void redirectError(HttpServletRequest request,
                               HttpServletResponse response,
                               String errorCode,
                               String registeredWith) throws IOException {

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(errorRedirectUrl)
                .queryParam("errorCode", errorCode);
        if (registeredWith != null) {
            builder.queryParam("registeredWith", registeredWith);
        }

        String targetUrl = builder.build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
