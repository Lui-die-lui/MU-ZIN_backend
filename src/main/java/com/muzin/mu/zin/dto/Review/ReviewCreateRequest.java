package com.muzin.mu.zin.dto.Review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReviewCreateRequest(
        @NotNull
        Long reservationId,

        @NotNull
        @Min(1)
        @Max(5)
        Integer rating,

        @Size(max = 255)
        String content,

        @Size(max = 3)
        List<Long> keywordIds
) {
}
