package com.muzin.mu.zin.dto.auth;

public record SigninRequest(
        String email,
        String password
) {}
