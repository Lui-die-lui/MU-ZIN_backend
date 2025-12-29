package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.entity.lesson.TimeSlotStatus;

import java.time.LocalDateTime;

public record TimeSlotResponse(
        Long timeSlotId,
        LocalDateTime startDt,
        LocalDateTime endDt,
        TimeSlotStatus status
) {
}
