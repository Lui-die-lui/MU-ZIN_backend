package com.muzin.mu.zin.dto.artist;

import com.muzin.mu.zin.entity.lesson.LessonMode;

import java.util.List;

public record ArtistLessonCardResponse(
        Long lessonId,
        String title,
        LessonMode mode,
        Integer price,
        Integer durationMin,
        ArtistInstrumentSummary instrument
) {
}
