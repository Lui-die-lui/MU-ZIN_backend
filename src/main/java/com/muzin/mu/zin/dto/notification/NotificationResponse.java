package com.muzin.mu.zin.dto.notification;

import com.muzin.mu.zin.entity.notification.NotificationRefType;
import com.muzin.mu.zin.entity.notification.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        String title,
        String content,
        NotificationRefType refType,
        Long refId,
        boolean isRead,
        LocalDateTime createDt
) {
}
