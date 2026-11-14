package com.muzin.mu.zin.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SupportRequest(
        @NotNull(message = "문의 유형을 골라주세요.")
        String category,            // ACCOUNT / RESERVATION / REPORT / BUG / ETC
        @NotBlank(message = "문의 내용을 입력해주세요.")
        @Size(max = 2000, message = "문의는 2000자까지 보낼 수 있어요.")
        String content
) {
}
