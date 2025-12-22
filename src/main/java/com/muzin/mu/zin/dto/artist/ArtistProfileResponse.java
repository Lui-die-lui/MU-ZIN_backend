package com.muzin.mu.zin.dto.artist;

import java.util.List;

public record ArtistProfileResponse(
        Long artistProfileId,
        Long userId,
        String bio,
        String career,
        String majorName,
        List<InstrumentResponse> instruments
) {
}
