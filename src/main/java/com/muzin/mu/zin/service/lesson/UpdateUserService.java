package com.muzin.mu.zin.service.lesson;

import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateUserService {

    private final UserRepository userRepository;

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
}
