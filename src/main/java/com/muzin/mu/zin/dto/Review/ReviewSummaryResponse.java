package com.muzin.mu.zin.dto.Review;

import java.util.List;

public record ReviewSummaryResponse(
        long reviewCount,
        double averageRating,                // 소수 첫째 자리 (리뷰 없으면 0)
        List<Long> ratingCounts,             // [5점, 4점, 3점, 2점, 1점] 개수
        List<ReviewKeywordCount> topKeywords // 많이 선택된 순 (같으면 태그 순서)
) {
}
