package com.muzin.mu.zin.dto.chat;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long chatMessageId,
        Long chatRoomId,
        Long senderUserId,
        String content,             // 삭제된 메시지는 빈 문자열
        LocalDateTime createDt,
        LocalDateTime editedDt,     // 수정했으면 시각 ("수정됨")
        boolean deleted
) {
}
