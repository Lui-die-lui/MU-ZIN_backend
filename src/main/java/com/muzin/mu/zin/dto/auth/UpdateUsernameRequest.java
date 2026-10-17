package com.muzin.mu.zin.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUsernameRequest(
        @NotBlank(message = "유저명을 입력해주세요.")
        @Size(max = 100, message = "유저명은 100자 이하로 입력해주세요.")
        String username
) {
}
