package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.entity.lesson.LessonMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record LessonCreateRequest(
        @NotBlank String title,
        @NotNull LessonMode mode,
        @NotNull Integer durationMin,
        Integer price,
        String description,
        String requirementText,
        List<Long> styleTagIds
        ) {
}
