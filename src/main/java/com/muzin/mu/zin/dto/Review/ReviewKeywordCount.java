package com.muzin.mu.zin.dto.Review;

// 리뷰 모음 상단 "많이 선택된 태그" 순위
public record ReviewKeywordCount(
        Long reviewKeywordId,
        String keywordName,
        long count
) {
}
