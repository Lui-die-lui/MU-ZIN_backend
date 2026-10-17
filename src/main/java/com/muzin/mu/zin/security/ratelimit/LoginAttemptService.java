package com.muzin.mu.zin.security.ratelimit;

import com.muzin.mu.zin.exception.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 로그인 실패 횟수 제한 (비밀번호 대입 공격 방지)
 *  - 이메일 기준으로 1시간 안에 5번 실패하면 1시간 동안 로그인 차단 (잠긴 동안은 맞는 비밀번호도 거부)
 *  - 로그인 성공 시 실패 횟수 초기화
 *  - 없는 이메일도 똑같이 세서 응답 차이로 가입 여부가 드러나지 않게 함
 *
 * 저장소: 서버 메모리 (서버 재시작 시 초기화됨. 서버를 여러 대로 늘리면 Redis 등 공유 저장소로 옮겨야 함)
 */
@Component
public class LoginAttemptService {

    public static final int MAX_FAILURES = 5;
    public static final Duration LOCK_DURATION = Duration.ofHours(1);
    // 실패 횟수를 세는 기간 (마지막 실패 후 이 시간이 지나면 처음부터 다시 셈)
    public static final Duration FAILURE_WINDOW = Duration.ofHours(1);

    private record Attempt(int failures, Instant lastFailureAt, Instant lockedUntil) { }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    // 테스트에서 시간을 조작하기 위한 생성자
    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    // 잠겨 있으면 429
    public void checkNotLocked(String email) {
        Attempt a = attempts.get(key(email));
        Instant now = clock.instant();
        if (a != null && a.lockedUntil() != null && now.isBefore(a.lockedUntil())) {
            throw locked(a.lockedUntil(), now);
        }
    }

    /**
     * 실패 1회 기록.
     * @return 잠기기 전까지 남은 시도 횟수 (이번 실패로 잠기면 429 예외)
     */
    public int recordFailure(String email) {
        Instant now = clock.instant();
        Attempt updated = attempts.compute(key(email), (k, prev) -> {
            // 처음이거나, 잠금이 풀렸거나, 마지막 실패가 오래됐으면 새로 셈
            boolean reset = prev == null
                    || (prev.lockedUntil() != null && !now.isBefore(prev.lockedUntil()))
                    || prev.lastFailureAt().plus(FAILURE_WINDOW).isBefore(now);
            int failures = (reset ? 0 : prev.failures()) + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? now.plus(LOCK_DURATION) : null;
            return new Attempt(failures, now, lockedUntil);
        });

        if (updated.lockedUntil() != null) {
            throw locked(updated.lockedUntil(), now);
        }
        return MAX_FAILURES - updated.failures();
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    // 오래된 기록 정리 (아무 이메일로나 시도하는 요청이 메모리에 쌓이지 않게)
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void evictExpired() {
        Instant now = clock.instant();
        attempts.entrySet().removeIf(e -> {
            Attempt a = e.getValue();
            if (a.lockedUntil() != null) return !now.isBefore(a.lockedUntil());
            return a.lastFailureAt().plus(FAILURE_WINDOW).isBefore(now);
        });
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException locked(Instant lockedUntil, Instant now) {
        long seconds = Duration.between(now, lockedUntil).toSeconds();
        long minutes = Math.max(1, (seconds + 59) / 60);
        return ApiException.tooManyRequests("LOGIN_LOCKED",
                "로그인에 " + MAX_FAILURES + "회 실패하여 1시간 동안 로그인할 수 없습니다. (" + minutes + "분 후 다시 시도 가능)",
                seconds);
    }
}
