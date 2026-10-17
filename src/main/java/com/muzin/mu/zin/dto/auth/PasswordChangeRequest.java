package com.muzin.mu.zin.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해주세요.")
        String newPassword // 컨펌은 프론트에서만 체크해도 ok
) {
}
