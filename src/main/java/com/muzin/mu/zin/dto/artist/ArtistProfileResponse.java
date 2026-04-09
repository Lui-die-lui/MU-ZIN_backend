package com.muzin.mu.zin.dto.artist;

import com.muzin.mu.zin.dto.instrument.InstrumentResponse;
import com.muzin.mu.zin.dto.region.MainRegionResponse;
import com.muzin.mu.zin.dto.region.ServiceRegionResponse;
import com.muzin.mu.zin.entity.ArtistStatus;

import java.util.List;

public record ArtistProfileResponse(
        Long artistProfileId,
        Long userId,
        String username,
        String profileImgUrl,
        String bio,
        String career,
        String majorName,
        ArtistStatus status,
        List<InstrumentResponse> instruments,
        MainRegionResponse mainRegion,
        List<ServiceRegionResponse> serviceRegions
) {
}
