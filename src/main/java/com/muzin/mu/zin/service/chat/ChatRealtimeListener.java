package com.muzin.mu.zin.service.chat;

import com.muzin.mu.zin.event.ChatMessageSentEvent;
import com.muzin.mu.zin.event.ChatReadEvent;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 메시지가 DB에 저장된(커밋된) 뒤에만 두 참여자에게 실시간 전달 -> 저장 실패한 메시지가 화면에 뜨지 않음
// 받는 곳: 각자 /user/queue/chat (STOMP 사용자 목적지, 사용자 이름 = userId)
@Component
@RequiredArgsConstructor
public class ChatRealtimeListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSent(ChatMessageSentEvent e) {
        for (Long userId : e.recipientUserIds()) {
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/chat", e.message());
        }
    }

    // 읽음: 상대의 /user/queue/chat-read 로 {chatRoomId, lastReadId}
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRead(ChatReadEvent e) {
        messagingTemplate.convertAndSendToUser(String.valueOf(e.recipientUserId()), "/queue/chat-read",
                Map.of("chatRoomId", e.chatRoomId(), "lastReadId", e.lastReadId()));
    }
}
