package com.muzin.mu.zin.dto.auth;

public record WithdrawRequest(
        String password,
        String confirmation
) {}
