package com.muzin.mu.zin.entity.reservation;

public enum ReservationStatus {
    REQUESTED,
    CONFIRMED,
    COMPLETION_PENDING,
    REJECTED,
    CANCELED,
    COMPLETED,
    EXPIRED // 아티스트가 응답하지 않은 채 레슨 시작 시간이 지난 요청 (스케줄러가 처리)
}
