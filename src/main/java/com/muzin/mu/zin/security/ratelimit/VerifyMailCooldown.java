package com.muzin.mu.zin.security.ratelimit;

import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.security.jwt.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 인증 메일 연속 발송 방지
 *  - 계정별로 발송 후 3분(= 인증 링크 유효시간) 동안 재발송 불가
 *  - 확인과 기록을 한 번에(원자적으로) 처리해서 동시에 두 번 눌러도 1통만 발송
 *  - 메일 서버 오류로 발송이 실패하면 release() 로 되돌려 바로 다시 시도 가능
 *
 * 저장소: 서버 메모리 (서버 재시작 시 초기화됨)
 */
@Component
public class VerifyMailCooldown {

    public static final Duration COOLDOWN = JwtUtils.VERIFY_TOKEN_TTL;

    private final ConcurrentHashMap<Long, Instant> lastSentAt = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public VerifyMailCooldown() {
        this(Clock.systemUTC());
    }

    VerifyMailCooldown(Clock clock) {
        this.clock = clock;
    }

    /**
     * 발송 권한 획득. 대기 시간 중이면 429.
     * @return 획득 시각 (발송 실패 시 release 에 넘김)
     */
    public Instant acquire(Long userId) {
        Instant now = clock.instant();
        Instant[] blockedUntil = new Instant[1];

        Instant result = lastSentAt.compute(userId, (k, prev) -> {
            if (prev != null && now.isBefore(prev.plus(COOLDOWN))) {
                blockedUntil[0] = prev.plus(COOLDOWN);
                return prev; // 그대로 유지
            }
            return now;
        });

        if (blockedUntil[0] != null) {
            long seconds = Duration.between(now, blockedUntil[0]).toSeconds() + 1;
            throw ApiException.tooManyRequests("MAIL_COOLDOWN",
                    "인증 메일은 3분에 한 번만 보낼 수 있습니다. " + formatRemaining(seconds) + " 후 다시 시도해주세요.",
                    seconds);
        }
        return result;
    }

    // 발송 실패 시 되돌림 (그 사이 다른 요청이 새로 획득했으면 건드리지 않음)
    public void release(Long userId, Instant acquiredAt) {
        lastSentAt.remove(userId, acquiredAt);
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void evictExpired() {
        Instant now = clock.instant();
        lastSentAt.entrySet().removeIf(e -> !now.isBefore(e.getValue().plus(COOLDOWN)));
    }

    private static String formatRemaining(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        if (m == 0) return s + "초";
        return s == 0 ? m + "분" : m + "분 " + s + "초";
    }
}
