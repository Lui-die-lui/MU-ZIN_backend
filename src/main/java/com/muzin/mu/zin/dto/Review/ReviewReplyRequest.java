package com.muzin.mu.zin.dto.Review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewReplyRequest(
        @NotNull
        Long reviewId,

        @NotBlank
        @Size(max = 255)
        String content
) {
}
