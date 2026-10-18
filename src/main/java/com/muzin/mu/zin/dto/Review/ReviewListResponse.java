package com.muzin.mu.zin.dto.Review;

import java.util.List;

// 레슨/아티스트 리뷰 모음 = 요약 + 목록
public record ReviewListResponse(
        ReviewSummaryResponse summary,
        List<ReviewResponse> reviews
) {
}
