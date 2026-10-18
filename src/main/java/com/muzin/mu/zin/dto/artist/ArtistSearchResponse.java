package com.muzin.mu.zin.dto.artist;

import com.muzin.mu.zin.dto.region.SearchMainRegionSummary;
import com.muzin.mu.zin.dto.region.ServiceRegionResponse;

import java.util.List;

public record ArtistSearchResponse(
        Long artistProfileId,
        String username,
        String majorName,
        String profileImgUrl,
        SearchMainRegionSummary mainRegion,
        List<ServiceRegionResponse> serviceRegions,
        List<ArtistInstrumentSummary> instruments,
        long followerCount,    // 팔로워 수 (누가 팔로우했는지는 공개하지 않음)
        boolean followedByMe   // 로그인한 사람이 팔로우 중인지 (비로그인은 false)
) {
}
