package com.muzin.mu.zin.dto.auth;

import com.muzin.mu.zin.entity.ArtistStatus;

import java.util.List;

public record PrincipalDto(
        Long userId,
        String email,
        String username,
        String profileImgUrl,
        Boolean emailVerified,
        ArtistStatus artistStatus,
        List<String> roles,

        // 비밀번호 변경
        Boolean canChangePassword,
        String oauth2Provider
) {
}
