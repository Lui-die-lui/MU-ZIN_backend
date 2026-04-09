package com.muzin.mu.zin.dto.region;

import java.util.List;

// 아티스트 프로필에 조합해서 붙임
public record ArtistRegionUpsertRequest(
        MainActivityRegionRequest mainRegion,
        List<ServiceRegionRequest> serviceRegions
) {
}
