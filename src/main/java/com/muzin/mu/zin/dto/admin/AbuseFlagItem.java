package com.muzin.mu.zin.dto.admin;

import java.time.LocalDateTime;

// 이상행동 감지 목록 한 줄
public record AbuseFlagItem(
        Long abuseFlagId,
        Long userId,
        String email,
        String username,
        String profileImgUrl,
        boolean suspended,
        String rule,          // CHAT_FLOOD / INQUIRY_SPAM / RESERVATION_SPAM / RESERVATION_CANCEL / LOGIN_FAILURE / REVIEW_BURST / REVIEW_LOW_RATING / REPORTED
        String detail,        // 화면에 그대로 보여줄 근거 문장
        int metric,
        String status,        // OPEN / DISMISSED / ACTIONED
        LocalDateTime detectedDt,
        LocalDateTime lastDetectedDt,
        String resolvedByName,
        LocalDateTime resolvedDt
) {
}
