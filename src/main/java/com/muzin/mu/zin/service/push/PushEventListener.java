package com.muzin.mu.zin.service.push;

import com.muzin.mu.zin.entity.notification.NotificationType;
import com.muzin.mu.zin.event.ChatMessageSentEvent;
import com.muzin.mu.zin.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

// 기존 이벤트(채팅 메시지 · 예약 알림)를 웹 푸시로도 보냄
//  - DB 에 커밋된 뒤에만 실행 (롤백되면 안 보냄), 실제 전송은 PushService 작업 스레드에서
//  - 여기서 예외가 나도 채팅·예약 처리에는 영향 없음
@Slf4j
@Component
@RequiredArgsConstructor
public class PushEventListener {

    private static final int CHAT_TTL = 60 * 60 * 6;        // 6시간 지난 채팅 알림은 의미가 적음
    private static final int RESERVATION_TTL = 60 * 60 * 24 * 2;

    // 푸시로 보낼 예약 알림 -> 문구 (상대 이름·레슨명은 넣지 않음)
    private static final Map<NotificationType, String> RESERVATION_BODY = Map.of(
            NotificationType.RESERVATION_REQUESTED, "새 예약 요청이 들어왔어요",
            NotificationType.RESERVATION_CONFIRMED, "예약이 확정됐어요",
            NotificationType.RESERVATION_REJECTED, "예약 요청이 거절됐어요",
            NotificationType.RESERVATION_CANCELED_BY_USER, "수강생이 예약을 취소했어요",
            NotificationType.RESERVATION_CANCELED_BY_ARTIST, "아티스트가 예약을 취소했어요",
            NotificationType.RESERVATION_EXPIRED, "응답이 없어 예약 요청이 만료됐어요"
    );

    private final PushService pushService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChat(ChatMessageSentEvent e) {
        if (!e.created()) return; // 수정·삭제 반영은 푸시하지 않음
        try {
            Long sender = e.message().senderUserId();
            long roomId = e.message().chatRoomId();
            e.recipientUserIds().stream()
                    .filter(id -> id != null && !id.equals(sender)) // 내가 보낸 메시지는 나에게 알리지 않음
                    .distinct()
                    .forEach(id -> pushService.sendToUser(id, PushMessage.chat(roomId), "chat-" + roomId, CHAT_TTL));
        } catch (RuntimeException ex) {
            log.warn("채팅 푸시 준비 실패: {}", ex.toString());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotification(NotificationEvent e) {
        String body = RESERVATION_BODY.get(e.type());
        if (body == null || e.recipientUserId() == null) return;
        try {
            // 아티스트가 받는 알림(요청·수강생 취소)은 요청 관리, 나머지는 내 예약
            boolean toArtist = e.type() == NotificationType.RESERVATION_REQUESTED
                    || e.type() == NotificationType.RESERVATION_CANCELED_BY_USER;
            String url = toArtist ? "/mypage/reservations/requests" : "/mypage/reservations";
            String tag = "reservation-" + (e.refId() == null ? "0" : e.refId());
            pushService.sendToUser(e.recipientUserId(), new PushMessage("MU:ZIN", body, url, tag), null, RESERVATION_TTL);
        } catch (RuntimeException ex) {
            log.warn("예약 푸시 준비 실패: {}", ex.toString());
        }
    }
}
