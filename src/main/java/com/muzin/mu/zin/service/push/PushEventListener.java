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

// 기존 이벤트(채팅 메시지 · 종 알림)를 웹 푸시로도 보냄
//  - DB 에 커밋된 뒤에만 실행 (롤백되면 안 보냄), 실제 전송은 PushService 작업 스레드에서
//  - 여기서 예외가 나도 채팅·예약 처리에는 영향 없음
@Slf4j
@Component
@RequiredArgsConstructor
public class PushEventListener {

    private static final int CHAT_TTL = 60 * 60 * 6;        // 6시간 지난 채팅 알림은 의미가 적음
    private static final int RESERVATION_TTL = 60 * 60 * 24 * 2;

    // 종 알림 종류 -> 푸시 문구 (잠금화면에 보이므로 상대 이름·레슨명은 넣지 않음)
    //  종 알림은 모두 푸시로도 보냄. 새 종류를 추가하면 여기에도 문구를 넣을 것 (없으면 알림 제목으로 보냄)
    private static final Map<NotificationType, String> BODY = Map.ofEntries(
            Map.entry(NotificationType.RESERVATION_REQUESTED, "새 예약 요청이 들어왔어요"),
            Map.entry(NotificationType.RESERVATION_CONFIRMED, "예약이 확정됐어요"),
            Map.entry(NotificationType.RESERVATION_REJECTED, "예약 요청이 거절됐어요"),
            Map.entry(NotificationType.RESERVATION_CANCELED_BY_USER, "수강생이 예약을 취소했어요"),
            Map.entry(NotificationType.RESERVATION_CANCELED_BY_ARTIST, "아티스트가 예약을 취소했어요"),
            Map.entry(NotificationType.RESERVATION_EXPIRED, "응답이 없어 예약 요청이 만료됐어요"),
            Map.entry(NotificationType.RESERVATION_COMPLETION_PENDING, "레슨이 끝났어요. 수업 완료를 확인해 주세요"),
            Map.entry(NotificationType.RESERVATION_COMPLETED, "레슨이 완료됐어요. 리뷰를 남길 수 있어요"),
            Map.entry(NotificationType.REVIEW_CREATED, "새 리뷰가 등록됐어요"),
            Map.entry(NotificationType.REVIEW_REPLIED, "내 리뷰에 답글이 달렸어요"),
            Map.entry(NotificationType.INQUIRY_CREATED, "새 레슨 문의가 도착했어요"),
            Map.entry(NotificationType.INQUIRY_REPLIED, "문의에 답변이 도착했어요"),
            Map.entry(NotificationType.INQUIRY_TO_CHAT, "문의가 채팅으로 이어졌어요"),
            Map.entry(NotificationType.ARTIST_APPROVED, "아티스트 신청이 승인됐어요"),
            Map.entry(NotificationType.ARTIST_REJECTED, "아티스트 신청 결과가 나왔어요"),
            Map.entry(NotificationType.SUPPORT_CREATED, "새 운영자 문의가 도착했어요"),
            Map.entry(NotificationType.SUPPORT_REPLIED, "운영자 문의에 답변이 도착했어요")
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
        if (!e.push() || e.type() == null || e.recipientUserId() == null) return;
        try {
            String body = BODY.getOrDefault(e.type(), e.title());
            String ref = e.refId() == null ? "0" : String.valueOf(e.refId());
            String tag = (e.type().name().startsWith("RESERVATION_") ? "reservation" : e.type().name().toLowerCase()) + "-" + ref;
            pushService.sendToUser(e.recipientUserId(), new PushMessage("MU:ZIN", body, urlFor(e), tag), null, RESERVATION_TTL);
        } catch (RuntimeException ex) {
            log.warn("알림 푸시 준비 실패: {}", ex.toString());
        }
    }

    // 눌렀을 때 열 화면 - 종 알림을 눌렀을 때(NavBar)와 같은 곳
    private static String urlFor(NotificationEvent e) {
        return switch (e.type()) {
            // 아티스트가 받는 예약 알림은 요청 관리
            case RESERVATION_REQUESTED, RESERVATION_CANCELED_BY_USER, RESERVATION_COMPLETION_PENDING -> "/mypage/reservations/requests";
            case RESERVATION_CONFIRMED, RESERVATION_REJECTED, RESERVATION_CANCELED_BY_ARTIST,
                 RESERVATION_EXPIRED, RESERVATION_COMPLETED -> "/mypage/reservations";
            case REVIEW_CREATED -> "/mypage/reviews?tab=received";
            case REVIEW_REPLIED -> "/mypage/reviews?tab=written";
            case INQUIRY_CREATED -> "/mypage/inquiries?tab=received";
            case INQUIRY_REPLIED -> "/mypage/inquiries?tab=sent";
            case INQUIRY_TO_CHAT -> e.refId() == null ? "/mypage/chats" : "/mypage/chats/" + e.refId();
            case ARTIST_APPROVED, ARTIST_REJECTED -> "/mypage/artist";
            case SUPPORT_CREATED -> "/admin/support";
            case SUPPORT_REPLIED -> "/mypage/support";
        };
    }
}
