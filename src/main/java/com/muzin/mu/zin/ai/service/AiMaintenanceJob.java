package com.muzin.mu.zin.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// AI 내비게이터 정리 작업 (주간 횟수 초기화는 하지 않음 - (user_id, week_start) 행으로 자동 구분)
@Slf4j
@Component
@RequiredArgsConstructor
public class AiMaintenanceJob {

    private final AiAssistantService service;

    // 처리 중 잠금이 만료된 질문 정리 (서버가 답변 도중 멈춘 경우)
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void recoverStale() {
        try {
            service.recoverStale();
        } catch (Exception e) {
            log.error("AI 잠금 만료 복구 실패", e);
        }
    }

    // 매일 04:20 (KST) 보관 기간 지난 대화·사용량 삭제
    @Scheduled(cron = "0 20 4 * * *", zone = "Asia/Seoul")
    public void cleanupRetention() {
        try {
            int n = service.cleanupRetention();
            if (n > 0) log.info("AI 대화 보관 기간 정리: {}건 삭제", n);
        } catch (Exception e) {
            log.error("AI 대화 보관 기간 정리 실패", e);
        }
    }
}
