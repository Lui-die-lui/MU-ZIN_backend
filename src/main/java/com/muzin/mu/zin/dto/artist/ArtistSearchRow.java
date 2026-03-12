package com.muzin.mu.zin.dto.artist;

public record ArtistSearchRow(
        Long artistProfileId,
        String username,
        String majorName,
        String email,
        String profileImgUrl
) {
}
