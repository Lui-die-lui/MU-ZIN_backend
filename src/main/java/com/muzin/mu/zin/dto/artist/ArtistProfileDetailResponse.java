package com.muzin.mu.zin.dto.artist;

import com.muzin.mu.zin.dto.instrument.InstrumentResponse;
import com.muzin.mu.zin.dto.lesson.LessonStyleTagResponse;
import com.muzin.mu.zin.dto.region.MainRegionResponse;
import com.muzin.mu.zin.dto.region.SearchMainRegionSummary;
import com.muzin.mu.zin.dto.region.SearchServiceRegionSummary;
import com.muzin.mu.zin.dto.region.ServiceRegionResponse;
import com.muzin.mu.zin.entity.ArtistStatus;

import java.util.List;

// 아티스트 검색 시 보여줄 디테일 목록들
public record ArtistProfileDetailResponse(
        Long artistProfileId,
        Long userId,
        String username,
        String profileImgUrl,
        String bio,
        String career,
        String majorName,
        ArtistStatus status,
        MainRegionResponse mainRegion,
        List<ServiceRegionResponse> serviceRegions,
        List<InstrumentResponse> instruments,
        List<LessonStyleTagResponse> styleTags
) {
}
