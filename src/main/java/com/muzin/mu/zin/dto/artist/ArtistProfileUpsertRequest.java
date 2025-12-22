package com.muzin.mu.zin.dto.artist;

// 없으면 생성, 있으면 수정
public record ArtistProfileUpsertRequest(
        String bio,
        String career,
        String majorName
) {
}
