package com.muzin.mu.zin.dto.Review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewReplyUpdateRequest(
        @NotBlank
        @Size(max = 255)
        String content
) {
}
