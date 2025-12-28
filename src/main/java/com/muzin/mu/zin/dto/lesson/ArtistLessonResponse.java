package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ArtistLessonResponse(
        Long lessonId,
        String title,
        String description,
        String requirementText,
        Integer price,
        Integer durationMin,
        LessonMode mode,
        LessonStatus status,
        List<LessonStyleTagResponse> styleTags,
        LocalDateTime createDt,
        LocalDateTime updateDt

) {
}
