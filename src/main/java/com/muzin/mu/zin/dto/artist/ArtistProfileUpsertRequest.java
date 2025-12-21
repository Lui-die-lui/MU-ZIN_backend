package com.muzin.mu.zin.dto.artist;

public record ArtistProfileUpsertRequest(
        String bio,
        String career,
        String majorName
) {
}
