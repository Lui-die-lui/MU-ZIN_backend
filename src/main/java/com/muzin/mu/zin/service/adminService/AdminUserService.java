package com.muzin.mu.zin.service.adminService;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 계정 정지/해제 - 정지되면 JWT 필터가 매 요청마다 확인해서 이미 발급된 토큰도 즉시 무효화됨
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;

    public void suspend(Long adminUserId, Long targetUserId, String reason) {
        if (adminUserId.equals(targetUserId)) {
            throw ApiException.badRequest("본인 계정은 정지할 수 없습니다.");
        }
        User user = getUserOrThrow(targetUserId);
        if (user.isSuspended()) {
            throw ApiException.conflict("이미 정지된 계정입니다.");
        }
        user.suspend(TimeDefaults.nowKst(), reason.trim());
        log.info("계정 정지: target={} by={}", targetUserId, adminUserId);
    }

    public void unsuspend(Long adminUserId, Long targetUserId) {
        User user = getUserOrThrow(targetUserId);
        if (!user.isSuspended()) {
            throw ApiException.conflict("정지된 계정이 아닙니다.");
        }
        user.unsuspend();
        log.info("계정 정지 해제: target={} by={}", targetUserId, adminUserId);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("유저를 찾을 수 없습니다."));
    }
}
