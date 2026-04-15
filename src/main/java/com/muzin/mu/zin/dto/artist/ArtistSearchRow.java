package com.muzin.mu.zin.dto.artist;

public record ArtistSearchRow(
        Long artistProfileId,
        String username,
        String majorName,
        String email,
        String profileImgUrl,
        String region1DepthName,
        String region2DepthName,
        String region3DepthName,
        String addressLabel
) {

}
