package com.muzin.mu.zin.event;

import com.muzin.mu.zin.dto.chat.ChatMessageResponse;

import java.util.List;

// 채팅 메시지 저장 후(커밋 후) 두 참여자에게 실시간 전달
public record ChatMessageSentEvent(List<Long> recipientUserIds, ChatMessageResponse message) {
}
