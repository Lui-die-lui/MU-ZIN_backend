package com.muzin.mu.zin.dto.auth;

public record SignupRequest(
        String email,
        String password,
        String passwordConfirm
) {}
