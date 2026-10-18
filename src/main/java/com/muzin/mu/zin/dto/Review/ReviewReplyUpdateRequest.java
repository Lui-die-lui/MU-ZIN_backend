package com.muzin.mu.zin.dto.Review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewReplyUpdateRequest(
        @NotBlank(message = "답글 내용을 입력해주세요.")
        @Size(max = 500, message = "500자 이하로 입력해주세요.")
        String content
) {
}
