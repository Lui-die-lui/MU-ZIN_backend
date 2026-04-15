package com.muzin.mu.zin.dto.artist;

import com.muzin.mu.zin.entity.instrument.InstrumentCategory;

import java.util.List;

public record ArtistSearchRequest(
        String keyword,
        InstrumentCategory instCategory,
        List<Long> instIds,
        List<Long> styleTagIds,
        String region1DepthName,
        String region2DepthName,
        String region3DepthName
) {
}
