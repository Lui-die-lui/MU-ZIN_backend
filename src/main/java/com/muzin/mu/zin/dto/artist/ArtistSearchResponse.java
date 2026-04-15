package com.muzin.mu.zin.dto.artist;

import com.muzin.mu.zin.dto.region.SearchMainRegionSummary;
import com.muzin.mu.zin.dto.region.ServiceRegionResponse;

import java.util.List;

public record ArtistSearchResponse(
        Long artistProfileId,
        String username,
        String majorName,
        String email,
        String profileImgUrl,
        SearchMainRegionSummary mainRegion,
        List<ServiceRegionResponse> serviceRegions,
        List<ArtistInstrumentSummary> instruments
) {
}
