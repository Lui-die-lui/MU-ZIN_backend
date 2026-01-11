package com.muzin.mu.zin.dto.admin;

import com.muzin.mu.zin.dto.instrument.InstrumentResponse;
import com.muzin.mu.zin.entity.ArtistStatus;
import com.muzin.mu.zin.entity.instrument.Instrument;

import java.time.LocalDateTime;
import java.util.List;

public record AdminArtistApplyDetail(
        Long artistProfileId,
        Long userId,
        String email,
        String username,
        ArtistStatus artistStatus,

        String bio,
        String career,
        String majorName,
        LocalDateTime submittedDt,
        LocalDateTime reviewedDt,

        String rejectReasonTitle,
        String rejectedReason,

        List<InstrumentResponse> instruments
) {

}
