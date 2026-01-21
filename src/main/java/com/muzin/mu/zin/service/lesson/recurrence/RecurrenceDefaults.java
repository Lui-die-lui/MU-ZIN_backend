package com.muzin.mu.zin.service.lesson.recurrence;

public final class RecurrenceDefaults {
    private RecurrenceDefaults() {}

    // 월 ~ 일 모두 on
    public static final int DEFAULT_DOW_MASK = 127;

    // 기본 반복 생성 값
    public static final int DEFAULT_WEEKS_AHEAD = 6;
    // 최대 생성 값
    public static final int MAX_WEEKS_AHEAD = 13;
}
