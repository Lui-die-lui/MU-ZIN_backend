package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.entity.lesson.LessonClosingPolicy;
import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// 부분 수정(PATCH): null 인 항목은 변경하지 않음
public record LessonUpdateRequest(
        @Size(min = 1, max = 100, message = "레슨 제목은 1~100자로 입력해주세요.")
        String title,
        LessonMode mode,
        String description,
        String requirementText,

        @PositiveOrZero(message = "가격은 0원 이상이어야 합니다.")
        Integer price,

        @Positive(message = "레슨 시간은 1분 이상이어야 합니다.")
        @Max(value = 1440, message = "레슨 시간은 하루(1440분)를 넘을 수 없습니다.")
        Integer durationMin,

        LessonStatus status,
        Long instId,
        LessonClosingPolicy closingPolicy
) {
}
