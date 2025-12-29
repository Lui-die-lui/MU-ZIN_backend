package com.muzin.mu.zin.dto.artist;

import com.muzin.mu.zin.dto.instrument.InstrumentResponse;
import com.muzin.mu.zin.entity.ArtistStatus;

import java.util.List;

public record ArtistProfileResponse(
        Long artistProfileId,
        Long userId,
        String bio,
        String career,
        String majorName,
        ArtistStatus status,
        List<InstrumentResponse> instruments
) {
}
