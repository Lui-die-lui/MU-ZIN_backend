package com.muzin.mu.zin.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InquiryRequest(
        @NotNull(message = "문의할 아티스트를 골라주세요.")
        Long artistProfileId,
        @NotBlank(message = "문의 내용을 입력해주세요.")
        @Size(max = 1000, message = "문의는 1000자까지 보낼 수 있어요.")
        String content
) {
}
