package com.muzin.mu.zin.service.notification;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.notification.NotificationResponse;
import com.muzin.mu.zin.entity.notification.Notification;
import com.muzin.mu.zin.repository.NotificationRepository;
import com.muzin.mu.zin.security.model.PrincipalUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // 내 알림 목록
    @Transactional(readOnly = true)
    public ApiRespDto<List<NotificationResponse>> getMyNotifications(PrincipalUser principalUser) {
        Long userId = principalUser.getUserId();
        List<Notification> list = notificationRepository.findAllByUser_UserIdOrderByCreateDtDesc(userId);

        List<NotificationResponse> resp = list.stream().map(this::toResponse).toList();
        return new ApiRespDto<>("success", "", resp);
    }

    // 총 알림 갯수
    @Transactional(readOnly = true)
    public ApiRespDto<Long> getMyUnreadCount(PrincipalUser principalUser) {
        Long userId = principalUser.getUserId();
        long count = notificationRepository.countByUser_UserIdAndIsReadFalse(userId);
        return new ApiRespDto<>("success", "", count);
    }

    // 단일 읽음 처리
    @Transactional
    public ApiRespDto<?> markRead(Long notificationId, PrincipalUser principalUser) {
        Long userId = principalUser.getUserId();

        Notification n = notificationRepository.findByNotificationIdAndUser_UserId(notificationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("잘못된 접근입니다."));

        if (!n.isRead()) n.markRead(); // 안읽음 -> 읽음(true로 바꿔줌)
        return new ApiRespDto<>("success", "읽음 처리 완료", null);
    }

    // 전체 읽음 처리
    @Transactional
    public ApiRespDto<?> markAllRead(PrincipalUser principalUser) {
        Long userId = principalUser.getUserId();

        List<Notification> list = notificationRepository.findAllByUser_UserIdOrderByCreateDtDesc(userId);
        list.stream()
                .filter(n -> !n.isRead()) // 안읽은것만 골라냄
                .forEach(Notification::markRead); // 각각 읽음 처리 해줌

        return new ApiRespDto<>("success", "전체 읽음 처리 완료", null);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getNotificationId(),
                n.getType(),
                n.getTitle(),
                n.getContent(),
                n.getRefType(),
                n.getRefId(),
                n.isRead(),
                n.getCreateDt()
        );
    }
}
