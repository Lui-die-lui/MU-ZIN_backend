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
        String oauth2Provider,

        // 지금 약관·처리방침에 동의하지 않았음 -> 화면이 동의 화면으로 보냄
        Boolean consentRequired
) {
}
