package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.entity.lesson.LessonMode;

import java.time.LocalDateTime;
import java.util.List;

public record ArtistLessonResponse(
        Long lessonId,
        String title,
        LessonMode mode,
        List<LessonStyleTagResponse> styleTags,
        LocalDateTime createDt,
        LocalDateTime updateDt

) {
}
