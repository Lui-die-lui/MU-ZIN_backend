package com.muzin.mu.zin.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record AdminArtistRejectRequest(
        @NotBlank(message = "반려 사유 제목은 필수입니다.")
        String title,
        @NotBlank(message = "반려 사유 제목은 필수입니다.")
        String reason
) {
}
