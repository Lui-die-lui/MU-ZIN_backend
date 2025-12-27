package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonStatus;

public record LessonUpdateRequest(
        String title,
        LessonMode mode,
        LessonStatus status
) {
}
