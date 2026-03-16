package com.muzin.mu.zin.dto.artist;

import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonStatus;

import java.util.List;

public record ArtistLessonDetailResponse(
        Long lessonId,
        String title,
        String description,
        String requirementText,
        Integer price,
        Integer durationMin,
        LessonMode mode,
        LessonStatus status,
        ArtistInstrumentSummary instrument
) {
}
