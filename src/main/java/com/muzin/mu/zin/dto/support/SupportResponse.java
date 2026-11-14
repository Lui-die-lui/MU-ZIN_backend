package com.muzin.mu.zin.dto.support;

import java.time.LocalDateTime;

// 내 운영자 문의 한 건 (답변한 관리자 이름은 보여주지 않음)
public record SupportResponse(
        Long supportId,
        String category,
        String content,
        String status,              // OPEN / ANSWERED
        String replyContent,
        LocalDateTime repliedDt,
        LocalDateTime createDt
) {
}
