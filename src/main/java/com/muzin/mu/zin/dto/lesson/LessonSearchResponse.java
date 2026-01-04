package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonStatus;

public record LessonSearchResponse(
        Long lessonId,
        String title,
        String description,
        Integer price,
        Integer durationMin,
        LessonMode mode,
        LessonStatus status
) {
}
