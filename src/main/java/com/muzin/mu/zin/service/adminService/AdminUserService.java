package com.muzin.mu.zin.service.adminService;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.admin.AdminUserListItem;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.repository.admin.ModerationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// 계정 정지/해제 - 정지되면 JWT 필터가 매 요청마다 확인해서 이미 발급된 토큰도 즉시 무효화됨
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;
    private final ModerationRepository moderationRepository;

    // 회원 목록 (최근 가입순) - q: 이메일/이름 일부, suspendedOnly: 정지된 계정만
    @Transactional(readOnly = true)
    public Page<AdminUserListItem> list(String q, boolean suspendedOnly, Pageable pageable) {
        String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Page<User> page = userRepository.searchForAdmin(keyword, suspendedOnly, pageable);

        List<Long> ids = page.getContent().stream().map(User::getUserId).toList();
        Set<Long> adminIds = ids.isEmpty() ? Set.of() : new HashSet<>(userRepository.findAdminUserIds(ids));

        return page.map(u -> new AdminUserListItem(
                u.getUserId(),
                u.getEmail(),
                u.getUsername(),
                u.getProfileImgUrl(),
                u.getArtistStatus(),
                Boolean.TRUE.equals(u.getEmailVerified()),
                adminIds.contains(u.getUserId()),
                u.isSample(),
                u.getCreateDt(),
                u.getSuspendedDt(),
                u.getSuspendedReason()
        ));
    }

    public void suspend(Long adminUserId, Long targetUserId, String reason) {
        if (adminUserId.equals(targetUserId)) {
            throw ApiException.badRequest("본인 계정은 정지할 수 없습니다.");
        }
        User user = getUserOrThrow(targetUserId);
        if (user.isSuspended()) {
            throw ApiException.conflict("이미 정지된 계정입니다.");
        }
        // 관리자끼리 서로 정지하지 못하게 (관리자 권한 해제는 DB 에서 직접)
        if (!userRepository.findAdminUserIds(List.of(targetUserId)).isEmpty()) {
            throw ApiException.badRequest("관리자 계정은 정지할 수 없습니다.");
        }
        user.suspend(TimeDefaults.nowKst(), reason.trim());
        // 이 유저에게 열려 있던 이상행동 감지·신고는 "조치함"으로 닫음
        moderationRepository.closeAllOpenFor(targetUserId, adminUserId, TimeDefaults.nowKst());
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
