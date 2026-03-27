package com.muzin.mu.zin.schduler;

import com.muzin.mu.zin.service.lesson.LessonReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LessonReservationScheduler {

    private final LessonReservationService lessonReservationService;

    // 레슨 종료 1시간 뒤 자동 종료 스케줄러
    @Scheduled(fixedDelay = 60000)
    public void moveToCompletionPending() {
        lessonReservationService.moveReservationToCompletionPending();
    }

    @Scheduled(fixedDelay = 60000)
    public void autoCompleteReservations() {
        lessonReservationService.autoCompleteReservations();
    }
}
