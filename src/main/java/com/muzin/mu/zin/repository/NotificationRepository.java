package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 알림 리스트 최신순부터 정렬
    List<Notification> findAllByUser_UserIdOrderByCreateDtDesc(Long userId);

    // 안읽은 알림 몇개인지
    long countByUser_UserIdAndIsReadFalse(Long userId);

    // 알림 단일 조회
    Optional<Notification> findByNotificationIdAndUser_UserId(Long notificationId, Long userId);
}
