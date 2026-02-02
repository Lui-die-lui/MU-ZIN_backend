package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.entity.TimePart;
import com.muzin.mu.zin.entity.lesson.TimeSlotStatus;

import java.time.LocalDateTime;
import java.util.List;

public record TimeSlotResponse(
        Long timeSlotId,
        LocalDateTime startDt,
        LocalDateTime endDt,
        TimeSlotStatus status
) {
}
