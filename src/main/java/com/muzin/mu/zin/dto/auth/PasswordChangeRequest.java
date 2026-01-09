package com.muzin.mu.zin.dto.auth;

public record PasswordChangeRequest(
        String currentPassword,
        String newPassword // 컨펌은 프론트에서만 체크해도 ok
) {
}
