package com.muzin.mu.zin.dto.chat;

import java.time.LocalDateTime;
import java.util.List;

// 채팅방 (목록·상세 공용)
public record ChatRoomResponse(
        Long chatRoomId,
        String myRole,                 // STUDENT / ARTIST
        Long counterpartUserId,
        String counterpartName,
        String counterpartProfileImgUrl,
        Long counterpartArtistProfileId, // 상대가 아티스트일 때 (아티스트 페이지 링크)
        String lastMessage,
        LocalDateTime lastMessageDt,
        long unreadCount,
        boolean writable,
        boolean counterpartLeft,       // 상대가 대화방을 나감 -> 읽기 전용
        LocalDateTime writableUntil,   // 진행 중 예약이 없을 때 대화 가능한 마감 (마지막 완료 레슨 + 14일), 진행 중이면 null
        Long lastReadIdOfCounterpart,  // 상대가 어디까지 읽었는지 (읽음 표시)
        List<ReservationSummary> reservations // 상세에서만 (최근 3개), 목록은 빈 배열
) {
    public record ReservationSummary(Long reservationId, String lessonTitle, LocalDateTime startDt, LocalDateTime endDt, String status) {
    }
}
