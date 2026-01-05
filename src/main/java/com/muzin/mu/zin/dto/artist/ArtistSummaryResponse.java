package com.muzin.mu.zin.dto.artist;

import com.muzin.mu.zin.dto.instrument.InstrumentResponse;

import java.util.List;

// 레슨 리스트, 레슨 디테일에서 보여줄 아티스트 요약 정보
public record ArtistSummaryResponse(
        Long artistProfileId,
        String username,
        String profileImgUrl
//        List<InstrumentResponse> instruments
) {
}
