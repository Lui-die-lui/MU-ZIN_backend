package com.muzin.mu.zin.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record SigninRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {}
