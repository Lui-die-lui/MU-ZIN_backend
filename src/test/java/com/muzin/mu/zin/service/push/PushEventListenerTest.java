package com.muzin.mu.zin.service.push;

import com.muzin.mu.zin.entity.notification.NotificationRefType;
import com.muzin.mu.zin.entity.notification.NotificationType;
import com.muzin.mu.zin.event.NotificationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 종 알림 -> 웹 푸시: 모든 종류가 푸시되고, 눌렀을 때 허용된 화면으로 가는지 (실제 발송 없음)
class PushEventListenerTest {

    private final PushService pushService = mock(PushService.class);
    private final PushEventListener listener = new PushEventListener(pushService);

    private PushMessage sent(NotificationEvent e) {
        listener.onNotification(e);
        ArgumentCaptor<PushMessage> msg = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).sendToUser(eq(7L), msg.capture(), isNull(), anyInt());
        return msg.getValue();
    }

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    @DisplayName("모든 종 알림 종류가 푸시되고, 기본 화면(/mypage)으로 떨어지지 않는다")
    void everyTypeIsPushed(NotificationType type) {
        PushMessage m = sent(new NotificationEvent(7L, type, "제목", "홍길동님이 보냈어요", NotificationRefType.RESERVATION, 3L));
        assertThat(m.url()).isNotEqualTo("/mypage");
        assertThat(m.body()).doesNotContain("홍길동"); // 잠금화면에 상대 이름 노출 안 함
    }

    @Test
    @DisplayName("문의가 채팅으로 이어지면 그 채팅방을 연다")
    void inquiryToChatOpensRoom() {
        PushMessage m = sent(new NotificationEvent(7L, NotificationType.INQUIRY_TO_CHAT, "t", "c", NotificationRefType.CHAT_ROOM, 42L));
        assertThat(m.url()).isEqualTo("/mypage/chats/42");
    }

    @Test
    @DisplayName("자동 완료처럼 push=false 인 알림은 푸시하지 않는다")
    void noPushWhenFlagOff() {
        listener.onNotification(new NotificationEvent(7L, NotificationType.RESERVATION_COMPLETED, "t", "c",
                NotificationRefType.RESERVATION, 3L, false));
        verifyNoInteractions(pushService);
    }
}
