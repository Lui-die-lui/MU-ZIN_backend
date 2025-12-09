package com.muzin.mu.zin.dto.auth;

public record UpdateUserProfileRequest(
        String username, String profileImgUrl
) {}
