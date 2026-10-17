package com.muzin.mu.zin.security.ratelimit;

import com.muzin.mu.zin.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitTest {

    // ---------- 로그인 5회 / 1시간 ----------

    @Test
    @DisplayName("로그인 4번 실패까지는 남은 횟수 안내, 5번째 실패에서 잠김(429)")
    void lockOnFifthFailure() {
        LoginAttemptService s = new LoginAttemptService(new MutableClock());

        assertThat(s.recordFailure("a@x.com")).isEqualTo(4);
        assertThat(s.recordFailure("a@x.com")).isEqualTo(3);
        assertThat(s.recordFailure("a@x.com")).isEqualTo(2);
        assertThat(s.recordFailure("a@x.com")).isEqualTo(1);

        assertThatThrownBy(() -> s.recordFailure("a@x.com"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getCode()).isEqualTo("LOGIN_LOCKED");
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(3600);
                });

        // 잠긴 동안에는 시도 자체가 막힘 (맞는 비밀번호여도)
        assertThatThrownBy(() -> s.checkNotLocked("a@x.com")).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("잠금은 정확히 1시간 뒤 풀리고, 풀리면 다시 5회 시도 가능")
    void unlockAfterOneHour() {
        MutableClock clock = new MutableClock();
        LoginAttemptService s = new LoginAttemptService(clock);
        for (int i = 0; i < 4; i++) s.recordFailure("a@x.com");
        assertThatThrownBy(() -> s.recordFailure("a@x.com")).isInstanceOf(ApiException.class);

        clock.advance(Duration.ofMinutes(59));
        assertThatThrownBy(() -> s.checkNotLocked("a@x.com"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(60));

        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> s.checkNotLocked("a@x.com")).doesNotThrowAnyException();
        assertThat(s.recordFailure("a@x.com")).isEqualTo(4); // 처음부터 다시 셈
    }

    @Test
    @DisplayName("이메일 대소문자/공백이 달라도 같은 계정으로 셈")
    void sameAccountIgnoringCase() {
        LoginAttemptService s = new LoginAttemptService(new MutableClock());
        s.recordFailure("A@X.com");
        s.recordFailure(" a@x.COM ");
        assertThat(s.recordFailure("a@x.com")).isEqualTo(2);
    }

    @Test
    @DisplayName("로그인 성공하면 실패 횟수 초기화, 다른 이메일은 영향 없음")
    void successResets() {
        LoginAttemptService s = new LoginAttemptService(new MutableClock());
        s.recordFailure("a@x.com");
        s.recordFailure("a@x.com");
        s.recordSuccess("a@x.com");
        assertThat(s.recordFailure("a@x.com")).isEqualTo(4);
        assertThat(s.recordFailure("b@x.com")).isEqualTo(4);
    }

    @Test
    @DisplayName("마지막 실패 후 1시간이 지나면 실패 횟수 초기화")
    void oldFailuresExpire() {
        MutableClock clock = new MutableClock();
        LoginAttemptService s = new LoginAttemptService(clock);
        for (int i = 0; i < 4; i++) s.recordFailure("a@x.com");
        clock.advance(Duration.ofMinutes(61));
        assertThat(s.recordFailure("a@x.com")).isEqualTo(4);
    }

    // ---------- 인증 메일 3분 ----------

    @Test
    @DisplayName("인증 메일은 3분 동안 재발송 불가, 3분 뒤 가능")
    void mailCooldown() {
        MutableClock clock = new MutableClock();
        VerifyMailCooldown c = new VerifyMailCooldown(clock);

        c.acquire(1L);
        assertThatThrownBy(() -> c.acquire(1L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getCode()).isEqualTo("MAIL_COOLDOWN");
                    assertThat(e.getRetryAfterSeconds()).isBetween(179L, 181L);
                });

        clock.advance(Duration.ofSeconds(179));
        assertThatThrownBy(() -> c.acquire(1L)).isInstanceOf(ApiException.class);

        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> c.acquire(1L)).doesNotThrowAnyException();

        // 다른 계정은 영향 없음
        assertThatCode(() -> c.acquire(2L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("발송 실패로 release 하면 바로 다시 보낼 수 있음")
    void releaseOnFailure() {
        VerifyMailCooldown c = new VerifyMailCooldown(new MutableClock());
        Instant acquired = c.acquire(1L);
        c.release(1L, acquired);
        assertThatCode(() -> c.acquire(1L)).doesNotThrowAnyException();
    }
}
