package com.muzin.mu.zin.service.lesson;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.auth.PasswordChangeRequest;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.repository.OAuth2UserRepository;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.security.model.PrincipalUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateUserService {

    private final UserRepository userRepository;
    private final OAuth2UserRepository oAuth2UserRepository;

    private final PasswordEncoder passwordEncoder;

    // 유저명 변경
    @Transactional
    public void updateUsername(Long userId, String username) {
        String next = username == null ? null : username.trim();
        if (next == null || next.isBlank()) {
            throw new IllegalArgumentException("유저명을 입력해주세요.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저가 존재하지 않습니다."));

        if (next.equals(user.getUsername())) return; // 변경 없으면 변경 없음

        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("이미 사용중인 이름입니다.");
        }

        user.setUsername(username);
    }

    // 프로필 이미지 변경
    @Transactional
    public void updateProfileImage(Long userId, String profileImgUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저가 존재하지 않습니다."));

        user.setProfileImgUrl(profileImgUrl);
    }

    // 비밀번호 변경
    @Transactional
    public ApiRespDto<?> changePassword(PrincipalUser principal, PasswordChangeRequest req) {
        if (principal == null) {
            return new ApiRespDto<>("failed","인증되지 않은 사용자입니다.", null);
        }

        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("해당 유저가 존재하지 않습니다."));

        // oauth2 유저 비밀번호 변경 차단
        boolean isOauth2 = oAuth2UserRepository.existsByUser_UserId(principal.getUserId());
        if (isOauth2) {
            return new ApiRespDto<>("failed","소셜 로그인 계정은 비밀번호 변경이 불가능합니다.", null);
        }

        // 현재 비밀번호 검증
        if (user.getPassword() == null || !passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            return new ApiRespDto<>("failed","현재 비밀번호가 올바르지 않습니다.", null);
        }

        // 새 비밀번호가 현재 비밀번호와 같은지
        if (passwordEncoder.matches(req.newPassword() , user.getPassword())) {
            return new ApiRespDto<>("failed", "새 비밀번호가 기존 비밀번호와 동일합니다.", null);
        }

        // 변경 저장
        user.setPassword(passwordEncoder.encode(req.newPassword()));

        return new ApiRespDto<>("success", "비밀번호가 변경되었습니다.", null);
    }
}
