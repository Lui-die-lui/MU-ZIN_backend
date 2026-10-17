package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.entity.lesson.LessonClosingPolicy;
import com.muzin.mu.zin.entity.lesson.LessonMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record LessonCreateRequest(
        @NotBlank(message = "레슨 제목을 입력해주세요.")
        @Size(max = 100, message = "레슨 제목은 100자 이하로 입력해주세요.")
        String title,

        @NotNull(message = "레슨 방식을 선택해주세요.")
        LessonMode mode,

        @NotNull(message = "레슨 시간을 입력해주세요.")
        @Positive(message = "레슨 시간은 1분 이상이어야 합니다.")
        @Max(value = 1440, message = "레슨 시간은 하루(1440분)를 넘을 수 없습니다.")
        Integer durationMin,

        @PositiveOrZero(message = "가격은 0원 이상이어야 합니다.")
        Integer price,

        String description,
        String requirementText,

        @NotNull(message = "악기를 선택해주세요.")
        Long instId,

        LessonClosingPolicy closingPolicy
        ) {
}
