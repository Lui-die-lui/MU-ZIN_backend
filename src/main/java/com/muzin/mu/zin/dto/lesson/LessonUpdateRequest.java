package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonStatus;

public record LessonUpdateRequest(
        String title,
        LessonMode mode,
        String description,
        String requirementText,
        Integer price,
        Integer durationMin,
        LessonStatus status
) {
}
