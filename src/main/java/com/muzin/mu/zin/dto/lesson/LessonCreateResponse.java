package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonStatus;

import java.util.List;

public record LessonCreateResponse(
        Long lessonId,
        String title,
        LessonMode mode,
        LessonStatus status,
        List<LessonStyleTagResponse> styleTags
        ) {
}
