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
        Integer rating,
        String content,
        List<ReviewKeywordResponse> keywords,
        ReviewReplyResponse reply,
        LocalDateTime createDt,
        LocalDateTime updateDt

) {
}
