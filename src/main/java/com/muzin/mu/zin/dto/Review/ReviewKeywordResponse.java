package com.muzin.mu.zin.dto.Review;

public record ReviewKeywordResponse(
        Long reviewKeywordId,
        String keywordName,
        Integer displayOrder
) {
}
