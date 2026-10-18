package com.muzin.mu.zin.entity.notification;

public enum NotificationType {
    RESERVATION_REQUESTED,
    RESERVATION_CONFIRMED,
    RESERVATION_REJECTED,
    RESERVATION_COMPLETION_PENDING,
    RESERVATION_COMPLETED,
    RESERVATION_CANCELED_BY_USER,
    RESERVATION_CANCELED_BY_ARTIST,
    RESERVATION_EXPIRED,
    REVIEW_CREATED, // 내 레슨에 리뷰가 달림 (-> 아티스트)
    REVIEW_REPLIED, // 내 리뷰에 아티스트 답글 (-> 리뷰 작성자)
    INQUIRY_CREATED, // 새 레슨 문의 (-> 아티스트)
    INQUIRY_REPLIED, // 문의 답변 (-> 문의한 사람)
    INQUIRY_TO_CHAT  // 아티스트가 문의를 채팅으로 넘김 (-> 문의한 사람)
    // 추후 NEW_MESSAGE 등 추가 예정
}
