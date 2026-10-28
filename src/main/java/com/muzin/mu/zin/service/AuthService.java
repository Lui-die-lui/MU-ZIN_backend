package com.muzin.mu.zin.service;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.auth.ConsentRequest;
import com.muzin.mu.zin.dto.auth.OAuth2SignupRequest;
import com.muzin.mu.zin.dto.auth.PrincipalDto;
import com.muzin.mu.zin.dto.auth.SigninRequest;
import com.muzin.mu.zin.dto.auth.SignupRequest;
import com.muzin.mu.zin.entity.OAuth2UserEntity;
import com.muzin.mu.zin.entity.Role;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.UserRole;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.OAuth2UserRepository;
import com.muzin.mu.zin.repository.RoleRepository;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.security.jwt.JwtUtils;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.security.ratelimit.LoginAttemptService;
import com.muzin.mu.zin.security.ratelimit.LoginFailureRecorder;
import lombok.RequiredArgsConstructor;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Map<String, String> PROVIDER_NAMES = Map.of(
            "google", "구글", "naver", "네이버", "kakao", "카카오");

    private final OAuth2UserRepository oAuth2UserRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final UsernameGenerator usernameGenerator;
    private final LoginAttemptService loginAttemptService;
    private final LoginFailureRecorder loginFailureRecorder;
    private final ConsentService consentService;
    private final WithdrawalService withdrawalService;

    // 회원가입
    @Transactional
    public void signup(SignupRequest request) {
        String email = request.email().trim();
        boolean consented = consentService.checkSignup(request.consent()); // 하나라도 "동의 안 함"이면 여기서 거부
        withdrawalService.checkRejoinAllowed(email);

        if (!request.password().equals(request.passwordConfirm())) {
            throw new IllegalArgumentException("비밀번호 확인이 일치하지 않습니다.");
        }

        // 이메일 중복 검증 (대소문자 무시) - 소셜로 가입된 이메일이면 어떤 소셜인지 알려줌
        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            List<OAuth2UserEntity> links = oAuth2UserRepository.findAllByUser_UserId(existing.get().getUserId());
            if (!links.isEmpty()) {
                String name = providerName(links.get(0).getProvider());
                throw ApiException.conflict("EMAIL_ALREADY_REGISTERED",
                        "이미 " + name + " 계정으로 가입된 이메일입니다. " + name + " 로그인을 이용해주세요.");
            }
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "이미 사용중인 이메일입니다.");
        }

        User newUser = User
                .builder()
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .username(usernameGenerator.generate(email)) // 공개 화면에 이메일이 노출되지 않게
                // emailVerified, artistStatus, userRoles 는 @Builder.Default로 기본값 사용
                .build();

        // 기본 ROLE_USER 조회
        Role userRoleEntity = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("해당 권한이 DB에 존재하지 않습니다."));

        // UserRole 생성 + 양방향 세팅
        UserRole userRole = UserRole.builder()
                .user(newUser)
                .role(userRoleEntity)
                .build();
        // 편의 메서드
        newUser.addUserRole(userRole);

        // 저장 (User에 cascade = ALL 걸려있어서 User만 save 해도 UserRole 같이 저장됨)
        userRepository.saveAndFlush(newUser);
        if (consented) consentService.agree(newUser.getUserId(), request.consent());
    }

    /*
     * 소셜 첫 가입 완료 - 소셜 로그인 직후에는 회원을 만들지 않고(OAuth2SuccessHandler) 동의 화면에서 이 요청이 오면 만듦
     * @return 로그인 토큰
     */
    @Transactional
    public String completeOAuth2Signup(OAuth2SignupRequest req) {
        JwtUtils.OAuth2Signup s;
        try {
            s = jwtUtils.parseOAuth2SignupToken(req.signupToken());
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("SIGNUP_TOKEN_INVALID", "가입 시간이 지났어요. 소셜 로그인을 다시 시도해 주세요.");
        }
        consentService.requireAll(req.consent()); // 동의가 빠졌으면 회원을 만들기 전에 거부
        return createOAuth2User(s, req);
    }

    private String createOAuth2User(JwtUtils.OAuth2Signup s, OAuth2SignupRequest req) {
        // 동의 화면에서 두 번 누른 경우 등 - 이미 연결돼 있으면 그 계정으로 로그인
        Optional<OAuth2UserEntity> linked = oAuth2UserRepository.findByProviderAndProviderUserId(s.provider(), s.providerUserId());
        if (linked.isPresent()) {
            User user = linked.get().getUser();
            if (user.isSuspended()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "이용이 정지된 계정입니다. 관리자에게 문의해주세요.");
            }
            return jwtUtils.generateAccessToken(user.getUserId());
        }
        String email = s.email().trim();
        withdrawalService.checkRejoinAllowed(email);
        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            List<OAuth2UserEntity> links = oAuth2UserRepository.findAllByUser_UserId(existing.get().getUserId());
            String name = links.isEmpty() ? "이메일" : providerName(links.get(0).getProvider());
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "이미 " + name + "(으)로 가입된 이메일입니다.");
        }

        // 비밀번호 로그인은 불가능한 랜덤값
        User newUser = User.builder()
                .email(email)
                .username(usernameGenerator.generate(email)) // 공개 화면에 이메일이 노출되지 않게
                .password(passwordEncoder.encode("oauth2-" + UUID.randomUUID()))
                .emailVerified(s.emailVerified())
                .build();
        Role userRoleEntity = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("해당 권한이 DB에 존재하지 않습니다."));
        newUser.addUserRole(UserRole.builder().user(newUser).role(userRoleEntity).build());
        userRepository.saveAndFlush(newUser);

        oAuth2UserRepository.save(OAuth2UserEntity.builder()
                .user(newUser)
                .provider(s.provider())
                .providerUserId(s.providerUserId())
                .build());
        consentService.agree(newUser.getUserId(), req.consent());
        return jwtUtils.generateAccessToken(newUser.getUserId());
    }

    // 기존 회원 다시 동의 (약관이 바뀌었거나 이 기능 이전에 가입)
    @Transactional
    public void agree(PrincipalUser principal, ConsentRequest req) {
        consentService.agree(principal.getUserId(), req);
    }

    @Transactional(readOnly = true)
    public ApiRespDto<String> signin(SigninRequest request) {
        String email = request.email().trim();

        // 5회 실패로 잠긴 이메일이면 비밀번호 확인 전에 429 (잠긴 동안은 맞는 비밀번호도 거부)
        loginAttemptService.checkNotLocked(email);

        Optional<User> found = userRepository.findByEmailIgnoreCase(email);

        // 이메일이 없든 비밀번호가 틀리든 같은 응답 + 같은 횟수 차감 (응답 차이로 가입 여부가 드러나지 않게)
        if (found.isEmpty() || !passwordEncoder.matches(request.password(), found.get().getPassword())) {
            loginFailureRecorder.record(email, found.map(User::getUserId).orElse(null)); // 이상행동 감지용 기록
            int remaining = loginAttemptService.recordFailure(email); // 5번째 실패면 여기서 429

            // 소셜 가입 계정은 비밀번호가 랜덤값이라 일반 로그인 불가 -> 가입 방식 안내
            // (회원가입 화면에서 이미 가입 여부를 알려주므로 여기서 알려줘도 추가로 노출되는 정보는 없음)
            if (found.isPresent()) {
                List<OAuth2UserEntity> links = oAuth2UserRepository.findAllByUser_UserId(found.get().getUserId());
                if (!links.isEmpty()) {
                    String name = providerName(links.get(0).getProvider());
                    throw ApiException.unauthorized("SOCIAL_ACCOUNT",
                            name + " 계정으로 가입된 이메일입니다. " + name + " 로그인을 이용해주세요.");
                }
            }
            throw ApiException.unauthorized("INVALID_CREDENTIALS",
                    "이메일 또는 비밀번호가 올바르지 않습니다. (남은 시도 " + remaining + "회)");
        }

        User user = found.get();
        loginAttemptService.recordSuccess(email);

        if (user.isSuspended()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "이용이 정지된 계정입니다. 관리자에게 문의해주세요.");
        }

        String accessToken = jwtUtils.generateAccessToken(user.getUserId());
        return new ApiRespDto<>("success", "로그인에 성공했습니다.", accessToken);
    }

    // principal getUsername(로그인 하게 해주는 값)이 email인데 username 컬럼이 존재해서 객체 다시 정제해서 보내야함
    @Transactional(readOnly = true)
    public ApiRespDto<PrincipalDto> getPrincipalDto(PrincipalUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("UNAUTHORIZED", "인증되지 않은 사용자입니다.");
        }

        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "해당 유저가 존재하지 않습니다."));

        // 소셜 연동이 여러 개일 수 있어서 List로 조회 (첫 번째 provider 표시)
        List<OAuth2UserEntity> links = oAuth2UserRepository.findAllByUser_UserId(user.getUserId());
        String oauth2Provider = links.isEmpty() ? null : links.get(0).getProvider();

        boolean canChangePassword = links.isEmpty(); // 소셜 로그인 유저가 아닐 때 비번 변경 가능

        PrincipalDto dto = new PrincipalDto(
                user.getUserId(),
                user.getEmail(),
                user.getUsername(),
                user.getProfileImgUrl(),
                user.getEmailVerified(),
                user.getArtistStatus(),
                principal.getRoles(),
                canChangePassword,
                oauth2Provider,
                consentService.isRequired(user.getUserId())
        );

        return new ApiRespDto<>("success","", dto);
    }

    private String providerName(String provider) {
        return PROVIDER_NAMES.getOrDefault(provider, provider);
    }
}
