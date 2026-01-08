package com.muzin.mu.zin.service;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.auth.PrincipalDto;
import com.muzin.mu.zin.dto.auth.SigninRequest;
import com.muzin.mu.zin.dto.auth.SignupRequest;
import com.muzin.mu.zin.entity.Role;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.UserRole;
import com.muzin.mu.zin.exception.DuplicateEmailException;
import com.muzin.mu.zin.repository.RoleRepository;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.security.jwt.JwtUtils;
import com.muzin.mu.zin.security.model.PrincipalUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    // 회원가입
    @Transactional
    public ApiRespDto<?> signup(SignupRequest request) {
//        Optional<User> user = userRepository.findByEmail(request.email());
//        if(user.isPresent()) {
//            return new ApiRespDto<>("failed", "이미 사용중인 이메일입니다.", null);
//        }
        // 이메일 중복 검증
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("이미 사용중인 이메일입니다.");
        }

        User newUser = User
                .builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .username(request.email())
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

        // 저장 (User에 cascade = ALL 걸려있으면 User만 save 해도 UserRole 같이 저장됨)
        userRepository.save(newUser);

        return new ApiRespDto<>("success", "회원가입이 완료되었습니다.", null);
    }

    public ApiRespDto<?> signin(SigninRequest request) {
        // 이메일로 유저 조회
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("이메일 또는 비밀번호가 올바르지 않습니다."));


        // 비밀번호 비교
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            return new ApiRespDto<>("failed", "이메일 또는 비밀번호가 올바르지 않습니다.",null);
        }

        String accessToken = jwtUtils.generateAccessToken(user.getUserId());
        return new ApiRespDto<>("success", "로그인에 성공했습니다.", accessToken);
    }

    // principal getUsername(로그인 하게 해주는 값)이 email인데 username 컬럼이 존재해서 객체 다시 정제해서 보내야함 진짜 짜증남
    @Transactional(readOnly = true)
    public ApiRespDto<PrincipalDto> getPrincipalDto(PrincipalUser principal) {
        if (principal == null) {
            return new ApiRespDto<>("failed", "인증되지 않은 사용자입니다.", null);
        }

        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("해당 유저가 존재하지 않습니다."));

        PrincipalDto dto = new PrincipalDto(
                user.getUserId(),
                user.getEmail(),
                user.getUsername(),
                user.getProfileImgUrl(),
                user.getEmailVerified(),
                user.getArtistStatus(),
                principal.getRoles()
        );

        return new ApiRespDto<>("success","", dto);
    }
}
