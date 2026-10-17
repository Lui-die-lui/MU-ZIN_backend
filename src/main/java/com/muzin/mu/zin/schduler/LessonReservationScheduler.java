package com.muzin.mu.zin.schduler;

import com.muzin.mu.zin.service.lesson.LessonReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 각 작업은 실패해도 다음 주기에 다시 시도됨. 예외는 로그로 남기고 다른 작업에 영향 주지 않음
@Slf4j
@Component
@RequiredArgsConstructor
public class LessonReservationScheduler {

    private final LessonReservationService lessonReservationService;

    // 확정된 레슨이 종료되면 완료 대기로 전환
    @Scheduled(fixedDelay = 60000)
    public void moveToCompletionPending() {
        run("moveToCompletionPending", lessonReservationService::moveReservationToCompletionPending);
    }

    // 레슨 종료 1시간 뒤 자동 완료
    @Scheduled(fixedDelay = 60000)
    public void autoCompleteReservations() {
        run("autoCompleteReservations", lessonReservationService::autoCompleteReservations);
    }

    // 응답 없이 시작 시간이 지난 예약 요청 만료
    @Scheduled(fixedDelay = 60000)
    public void expireUnansweredRequests() {
        run("expireUnansweredRequests", lessonReservationService::expireUnansweredRequests);
    }

    private void run(String name, Runnable job) {
        try {
            job.run();
        } catch (Exception e) {
            log.error("스케줄러 작업 실패: {}", name, e);
        }
    }
}
