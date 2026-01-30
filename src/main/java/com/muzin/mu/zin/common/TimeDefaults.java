package com.muzin.mu.zin.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

// 서비스 기본 시간을 한국으로 맞춰줌(예약 편차 생기지 않게)
public final class TimeDefaults {
    private TimeDefaults() {}
    public static final String DEFAULT_TZ = "Asia/Seoul";
    public static ZoneId DEFAULT_ZONE = ZoneId.of(DEFAULT_TZ);

    // 서울 기준 현재 날짜 시간
    public static LocalDateTime nowKst() {
        return LocalDateTime.now(DEFAULT_ZONE);
    }
    // 현재 날짜까지
    public static LocalDate todayKst() {
        return LocalDate.now(DEFAULT_ZONE);
    }
    // 오늘 날짜의 첫번째 시간
    public static LocalDateTime startOfTodayKst() {
        return LocalDate.now(DEFAULT_ZONE).atStartOfDay();
    }
}
