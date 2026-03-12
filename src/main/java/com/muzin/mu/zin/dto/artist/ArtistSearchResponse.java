package com.muzin.mu.zin.dto.artist;

import java.util.List;

public record ArtistSearchResponse(
        Long artistProfileId,
        String username,
        String majorName,
        String email,
        String profileImgUrl,
        List<ArtistInstrumentSummary> instruments
) {
}
