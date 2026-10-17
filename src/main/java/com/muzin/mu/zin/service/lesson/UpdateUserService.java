package com.muzin.mu.zin.service.lesson;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.auth.PasswordChangeRequest;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.exception.ApiException;
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
                .orElseThrow(() -> ApiException.notFound("해당 유저가 존재하지 않습니다."));

        if (next.equals(user.getUsername())) return; // 변경 없으면 변경 없음

        // 공백 제거한 값 기준으로 중복 검사 + 저장
        if (userRepository.existsByUsername(next)) {
            throw ApiException.conflict("USERNAME_TAKEN", "이미 사용중인 이름입니다.");
        }

        user.setUsername(next);
    }

    // 프로필 이미지 변경
    @Transactional
    public void updateProfileImage(Long userId, String profileImgUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("해당 유저가 존재하지 않습니다."));

        // 업로드 저장소(Firebase Storage) 주소만 허용 - 임의 외부 주소/스크립트 URL 저장 방지
        if (profileImgUrl != null && !profileImgUrl.isBlank()
                && !profileImgUrl.startsWith("https://firebasestorage.googleapis.com/")) {
            throw new IllegalArgumentException("허용되지 않은 이미지 주소입니다.");
        }

        user.setProfileImgUrl(profileImgUrl == null || profileImgUrl.isBlank() ? null : profileImgUrl);
    }

    // 비밀번호 변경
    @Transactional
    public ApiRespDto<?> changePassword(PrincipalUser principal, PasswordChangeRequest req) {
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> ApiException.notFound("해당 유저가 존재하지 않습니다."));

        // oauth2 유저 비밀번호 변경 차단
        if (oAuth2UserRepository.existsByUser_UserId(principal.getUserId())) {
            throw ApiException.forbidden("SOCIAL_ACCOUNT", "소셜 로그인 계정은 비밀번호 변경이 불가능합니다.");
        }

        // 현재 비밀번호 검증
        if (user.getPassword() == null || !passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw ApiException.badRequest("현재 비밀번호가 올바르지 않습니다.");
        }

        // 새 비밀번호가 현재 비밀번호와 같은지
        if (passwordEncoder.matches(req.newPassword() , user.getPassword())) {
            throw ApiException.badRequest("새 비밀번호가 기존 비밀번호와 동일합니다.");
        }

        // 변경 저장
        user.setPassword(passwordEncoder.encode(req.newPassword()));

        return new ApiRespDto<>("success", "비밀번호가 변경되었습니다.", null);
    }
}
