package com.muzin.mu.zin.dto.Review;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewResponse(
        Long reviewId,
        Long reservationId,
        Long lessonId,
        String lessonTitle,
        Long artistProfileId,
        String artistName,
        Long reviewerUserId,
        String reviewerName,
        String reviewerProfileImgUrl,
        Integer rating,
        String content,
        List<ReviewKeywordResponse> keywords,
        ReviewReplyResponse reply,
        LocalDateTime lessonStartDt, // 언제 받은 레슨인지
        LocalDateTime createDt,
        LocalDateTime updateDt,
        LocalDateTime editableUntil // 작성자가 수정할 수 있는 마감 (작성 후 7일)
) {
}
