package com.muzin.mu.zin.dto.chat;

import java.time.LocalDateTime;

public record InquiryResponse(
        Long inquiryId,
        String myRole,              // SENDER(문의한 사람) / ARTIST(받은 아티스트)
        Long artistProfileId,
        String artistName,
        String artistProfileImgUrl,
        Long userId,
        String userName,
        String userProfileImgUrl,
        String content,
        String replyContent,
        LocalDateTime repliedDt,
        String status,              // OPEN / REPLIED / CHAT
        Long chatRoomId,            // 채팅으로 넘긴 경우 (방이 지워졌으면 null)
        LocalDateTime createDt
) {
}
