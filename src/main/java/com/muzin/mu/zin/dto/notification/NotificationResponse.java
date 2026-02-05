package com.muzin.mu.zin.dto.notification;

import com.muzin.mu.zin.entity.notification.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        String title,
        String content,
        Long refReservationId,
        boolean isRead,
        LocalDateTime createDt
) {
}
