package com.muzin.mu.zin.dto.artist;

// 프론트에 리스트로 넘기기 위해서 해당 레코드 따로 작성
public record ArtistInstrumentSummary(
        Long instId,
        String instName
) {
}
