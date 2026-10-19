package com.muzin.mu.zin.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminArtistRejectRequest(
        @NotBlank(message = "반려 사유 제목은 필수입니다.")
        @Size(max = 100, message = "반려 사유 제목은 100자 이하로 입력해주세요.")
        String title,
        @NotBlank(message = "반려 사유 내용은 필수입니다.")
        @Size(max = 2000, message = "반려 사유 내용은 2000자 이하로 입력해주세요.")
        String reason
) {
}
