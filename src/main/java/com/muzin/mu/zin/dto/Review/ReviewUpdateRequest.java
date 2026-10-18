package com.muzin.mu.zin.dto.Review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReviewUpdateRequest(
        @NotNull(message = "별점을 골라주세요.")
        @Min(value = 1, message = "별점은 1~5점이에요.")
        @Max(value = 5, message = "별점은 1~5점이에요.")
        Integer rating,

        @Size(max = 500, message = "500자 이하로 입력해주세요.")
        String content,

        @Size(max = 3, message = "태그는 최대 3개까지 고를 수 있어요.")
        List<Long> keywordIds
) {
}
