package com.muzin.mu.zin.service.push;

import com.muzin.mu.zin.repository.push.PushSubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class PushServiceTest {

    private final PushSubscriptionRepository repo = mock(PushSubscriptionRepository.class);

    private PushService service(String pub, String priv) {
        return new PushService(repo, pub, priv, "", "http://localhost:5173");
    }

    @Test
    @DisplayName("푸시 서버 응답별 구독 정리: 2xx 성공 기록 / 404·410 삭제 / 그 외 실패 누적")
    void applyResult() {
        PushService s = service("", "");
        s.applyResult(1, 201);
        verify(repo).markSuccess(eq(1L), any());
        s.applyResult(2, 410);
        s.applyResult(3, 404);
        verify(repo).deleteById(2);
        verify(repo).deleteById(3);
        s.applyResult(4, 429);
        s.applyResult(5, 500);
        s.applyResult(6, 400);
        verify(repo).markFailure(4, PushService.DELETE_AFTER_FAILURES);
        verify(repo).markFailure(5, PushService.DELETE_AFTER_FAILURES);
        verify(repo).markFailure(6, PushService.DELETE_AFTER_FAILURES);
    }

    @Test
    @DisplayName("키가 없거나 잘못되면 푸시만 꺼지고, 보내기 요청은 조용히 무시")
    void disabledWithoutKeys() {
        assertThat(service("", "").enabled()).isFalse();
        assertThat(service("not-a-key", "also-bad").enabled()).isFalse();
        PushService off = service("", "");
        off.sendToUser(1, PushMessage.chat(1), "chat-1", 60);
        verify(repo, never()).findByUser(anyLong());
        verify(repo, never()).markFailure(anyLong(), anyInt());
    }
}
