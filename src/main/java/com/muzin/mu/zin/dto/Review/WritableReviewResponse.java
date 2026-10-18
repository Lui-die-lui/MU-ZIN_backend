package com.muzin.mu.zin.dto.Review;

import java.time.LocalDateTime;

// 리뷰를 아직 쓰지 않은 완료 레슨 (작성 기간 안)
public record WritableReviewResponse(
        Long reservationId,
        Long lessonId,
        String lessonTitle,
        Long artistProfileId,
        String artistName,
        LocalDateTime lessonStartDt,
        LocalDateTime lessonEndDt,
        LocalDateTime completedDt,
        LocalDateTime writableUntil
) {
}
