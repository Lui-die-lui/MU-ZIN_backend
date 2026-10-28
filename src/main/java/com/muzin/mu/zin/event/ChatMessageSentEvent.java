package com.muzin.mu.zin.event;

import com.muzin.mu.zin.dto.chat.ChatMessageResponse;

import java.util.List;

// 채팅 메시지 저장 후(커밋 후) 두 참여자에게 실시간 전달
// created: 새 메시지면 true, 수정·삭제 반영이면 false (웹 푸시는 새 메시지만)
public record ChatMessageSentEvent(List<Long> recipientUserIds, ChatMessageResponse message, boolean created) {
}
