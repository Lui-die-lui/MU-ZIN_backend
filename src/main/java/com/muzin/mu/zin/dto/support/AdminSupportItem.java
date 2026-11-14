package com.muzin.mu.zin.dto.support;

import java.time.LocalDateTime;

// 관리자 운영자 문의 목록 한 줄
public record AdminSupportItem(
        Long supportId,
        Long userId,
        String userName,
        String userEmail,
        boolean userSuspended,
        String category,
        String content,
        String status,              // OPEN / ANSWERED
        String replyContent,
        String repliedByName,
        LocalDateTime repliedDt,
        LocalDateTime createDt
) {
}
