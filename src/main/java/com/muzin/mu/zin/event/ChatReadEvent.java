package com.muzin.mu.zin.event;

// 읽음 처리 후(커밋 후) 상대에게 "여기까지 읽었어요" 실시간 전달 -> 상대 화면의 "읽음" 표시가 바로 바뀜
public record ChatReadEvent(Long recipientUserId, Long chatRoomId, Long lastReadId) {
}
