package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.dto.artist.ArtistProfileResponse;
import com.muzin.mu.zin.entity.lesson.LessonClosingPolicy;
import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ArtistLessonResponse(
        Long lessonId,
        String title,
        Long instId,
        String description,
        String requirementText,
        Integer price,
        Integer durationMin,
        LessonMode mode,
        LessonStatus status,
        List<LessonStyleTagResponse> styleTags,
        LessonClosingPolicy closingPolicy,
        LocalDateTime createDt,
        LocalDateTime updateDt
) {
}
