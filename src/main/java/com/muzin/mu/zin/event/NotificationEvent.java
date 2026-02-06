package com.muzin.mu.zin.event;

import com.muzin.mu.zin.entity.notification.NotificationRefType;
import com.muzin.mu.zin.entity.notification.NotificationType;

public record NotificationEvent(
        Long recipientUserId, // 컬럼 없어도 됨, 이벤트 (메모리에서 잠깐 전달되는 메시지)
        NotificationType type,
        String title,
        String content,
        NotificationRefType refType,
        Long refId
) {
}
