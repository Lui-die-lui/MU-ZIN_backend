package com.muzin.mu.zin.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryReplyRequest(
        @NotBlank(message = "답변 내용을 입력해주세요.")
        @Size(max = 1000, message = "답변은 1000자까지 보낼 수 있어요.")
        String content
) {
}
