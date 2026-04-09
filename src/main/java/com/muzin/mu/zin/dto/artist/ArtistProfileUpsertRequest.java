package com.muzin.mu.zin.dto.artist;

import com.muzin.mu.zin.dto.region.MainActivityRegionRequest;
import com.muzin.mu.zin.dto.region.ServiceRegionRequest;

import java.util.List;

// 없으면 생성, 있으면 수정
public record ArtistProfileUpsertRequest(
        String bio,
        String career,
        String majorName,
        MainActivityRegionRequest mainRegion,
        List<ServiceRegionRequest> serviceRegions
) {
}
