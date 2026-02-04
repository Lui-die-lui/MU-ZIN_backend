package com.muzin.mu.zin.entity.lesson;

public enum TimeSlotStatus {
    OPEN, // 오픈 상태 슬롯, 아티스트 거절, 유저 취소, 만료(고려해보기)
    PENDING, // 유저가 예약 요청 제출(선점 형식)
    BOOKED, // 아티스트 승인
    CLOSED
}
