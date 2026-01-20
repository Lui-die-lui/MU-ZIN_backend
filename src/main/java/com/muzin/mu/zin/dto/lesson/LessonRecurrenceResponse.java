package com.muzin.mu.zin.dto.lesson;

import java.time.LocalDate;
import java.time.LocalTime;

public record LessonRecurrenceResponse(
        Long ruleId,
        Long lessonId,
        Boolean enabled,
        String timezone,
        Integer daysOfWeekMask,
        LocalTime startTime,
        LocalTime endTime,
        Integer intervalMin,
        Integer weeksAhead,
        LocalDate materializedUntil
) {
}
