package com.muzin.mu.zin.dto.admin;

import com.muzin.mu.zin.entity.ArtistStatus;

import java.time.LocalDateTime;

// 관리자 회원 목록 한 줄
public record AdminUserListItem(
        Long userId,
        String email,
        String username,
        String profileImgUrl,
        ArtistStatus artistStatus,
        boolean emailVerified,
        boolean admin,
        boolean sample,
        LocalDateTime createDt,
        LocalDateTime suspendedDt, // null 이면 정상
        String suspendedReason
) {
}
