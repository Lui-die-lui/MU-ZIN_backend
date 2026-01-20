package com.muzin.mu.zin.common;

import java.time.ZoneId;

// 서비스 기본 시간을 한국으로 맞춰줌(예약 편차 생기지 않게)
public final class TimeDefaults {
    private TimeDefaults() {}
    public static final String DEFAULT_TZ = "Asia/Seoul";
    public static ZoneId DEFAULT_ZONE = ZoneId.of(DEFAULT_TZ);
}
