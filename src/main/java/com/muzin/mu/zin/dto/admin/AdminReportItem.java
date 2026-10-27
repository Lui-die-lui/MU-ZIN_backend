package com.muzin.mu.zin.dto.admin;

import java.time.LocalDateTime;

// 관리자 신고 목록 한 줄
public record AdminReportItem(
        Long reportId,
        Long reporterUserId,
        String reporterName,
        String reporterEmail,
        Long targetUserId,
        String targetName,
        String targetEmail,
        String targetProfileImgUrl,
        boolean targetSuspended,
        int targetOpenReports,  // 이 유저에게 처리 안 된 신고 수 (이 건 포함)
        String targetType,      // ARTIST / CHAT_ROOM / REVIEW / INQUIRY
        Long targetRefId,
        String reason,          // SPAM / ABUSE / FRAUD / INAPPROPRIATE / ETC
        String detail,
        String contentSnapshot, // 신고 당시 내용
        String status,          // OPEN / DISMISSED / ACTIONED
        String adminMemo,
        String resolvedByName,
        LocalDateTime createDt,
        LocalDateTime resolvedDt
) {
}
