package com.muzin.mu.zin.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminSuspendRequest(
        @NotBlank(message = "정지 사유를 입력해주세요.")
        @Size(max = 255, message = "정지 사유는 255자 이하로 입력해주세요.")
        String reason
) {
}
