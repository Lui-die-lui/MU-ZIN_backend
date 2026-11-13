package com.muzin.mu.zin.event;

import com.muzin.mu.zin.entity.notification.NotificationRefType;
import com.muzin.mu.zin.entity.notification.NotificationType;

public record NotificationEvent(
        Long recipientUserId, // 컬럼 없어도 됨, 이벤트 (메모리에서 잠깐 전달되는 메시지)
        NotificationType type,
        String title,
        String content,
        NotificationRefType refType,
        Long refId,
        boolean push // false 면 종 알림만 남기고 휴대폰 푸시는 보내지 않음 (예: 자동 완료)
) {
    // 기본은 푸시도 보냄
    public NotificationEvent(Long recipientUserId, NotificationType type, String title, String content,
                             NotificationRefType refType, Long refId) {
        this(recipientUserId, type, title, content, refType, refId, true);
    }
}
