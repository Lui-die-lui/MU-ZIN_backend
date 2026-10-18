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
        String region3DepthName,
        Integer page,
        Integer size,
        Boolean followedOnly // 내가 팔로우한 아티스트만 (로그인 필요)
) {
}
