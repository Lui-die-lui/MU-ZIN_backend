package com.muzin.mu.zin.service;

import com.muzin.mu.zin.dto.auth.ConsentRequest;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.ConsentRepository;
import com.muzin.mu.zin.repository.ConsentRepository.Versions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConsentServiceTest {

    private final ConsentRepository repo = mock(ConsentRepository.class);
    private final ConsentRequest all = new ConsentRequest(true, true, true);

    private ConsentService service(boolean strict) {
        return new ConsentService(repo, "T2", "P2", strict);
    }

    @Test
    @DisplayName("가입 요청: 셋 다 동의하면 기록 대상, 하나라도 거부하면 400")
    void signupConsent() {
        assertThat(service(false).checkSignup(all)).isTrue();
        assertThatThrownBy(() -> service(false).checkSignup(new ConsentRequest(true, true, false))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service(false).checkSignup(new ConsentRequest(true, null, true))).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("동의 항목이 아예 없는 요청(배포 전 화면): 기본은 받아 두고, 엄격 모드면 거부")
    void missingConsent() {
        assertThat(service(false).checkSignup(new ConsentRequest(null, null, null))).isFalse();
        assertThat(service(false).checkSignup(null)).isFalse();
        assertThatThrownBy(() -> service(true).checkSignup(new ConsentRequest(null, null, null))).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("다시 동의 필요: 기록 없음 / 약관이나 처리방침 버전이 바뀜")
    void required() {
        when(repo.latest(1)).thenReturn(Optional.empty());
        when(repo.latest(2)).thenReturn(Optional.of(new Versions("T2", "P2")));
        when(repo.latest(3)).thenReturn(Optional.of(new Versions("T1", "P2")));
        when(repo.latest(4)).thenReturn(Optional.of(new Versions("T2", "P1")));
        ConsentService s = service(false);
        assertThat(s.isRequired(1)).isTrue();
        assertThat(s.isRequired(2)).isFalse();
        assertThat(s.isRequired(3)).isTrue();
        assertThat(s.isRequired(4)).isTrue();
    }

    @Test
    @DisplayName("동의 저장은 셋 다 동의했을 때만, 지금 버전으로")
    void agree() {
        ConsentService s = service(false);
        s.agree(7, all);
        verify(repo).insert(eq(7L), eq("T2"), eq("P2"), eq(true), any());
        assertThatThrownBy(() -> s.agree(8, new ConsentRequest(true, true, null))).isInstanceOf(ApiException.class);
        verify(repo, never()).insert(eq(8L), anyString(), anyString(), anyBoolean(), any());
    }
}
