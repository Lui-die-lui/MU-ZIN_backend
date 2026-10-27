package com.muzin.mu.zin.security.ratelimit;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.repository.admin.ModerationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

// 로그인 실패를 DB 에 남김 (이상행동 감지 · 대시보드용, 30일 보관)
//  - 로그인은 실패하면 예외로 끝나서 같은 트랜잭션이면 같이 취소됨 -> 따로 커밋 (REQUIRES_NEW)
//  - 기록이 실패해도 로그인 응답에는 영향 없게
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginFailureRecorder {

    private final ModerationRepository moderationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String email, Long userId) {
        try {
            String key = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
            if (key.length() > 100) key = key.substring(0, 100);
            moderationRepository.insertLoginFailure(key, userId, TimeDefaults.nowKst());
        } catch (RuntimeException e) {
            log.warn("로그인 실패 기록 저장 실패: {}", e.getMessage());
        }
    }
}
