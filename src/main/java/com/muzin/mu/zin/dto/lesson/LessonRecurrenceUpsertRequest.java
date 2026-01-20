package com.muzin.mu.zin.dto.lesson;

import java.time.LocalTime;

public record LessonRecurrenceUpsertRequest(
        Boolean enabled,
        String timezone,
        Integer daysOfWeekMask, // 127 = 매일
        LocalTime startTime,
        LocalTime endTime,
        Integer intervalMin, // durationMin + 준비 시간
        Integer weeksAhead // 반복 주간 언제까지 할지 ?
) {
}
