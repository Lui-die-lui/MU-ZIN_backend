package com.muzin.mu.zin.dto.admin;

import com.muzin.mu.zin.entity.ArtistStatus;

import java.time.LocalDateTime;

public record AdminArtistApplyListItem(
        Long artistProfileId,
        Long userId,
        String email,
        String username,
        ArtistStatus artistStatus,
        LocalDateTime submittedDt, // 제출 날짜
        LocalDateTime reviewedDt, // 처리된 건인지, 언제 처리했는지
        String rejectReasonTitle // 반려한 이유 요약해서 볼 수 있음
) {
}
