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

    // 프로필 이미지 변경
    @Transactional
    public void updateProfileImage(Long userId, String profileImgUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저가 존재하지 않습니다."));

        user.setProfileImgUrl(profileImgUrl);
    }
}
