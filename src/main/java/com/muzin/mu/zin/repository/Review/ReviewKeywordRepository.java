package com.muzin.mu.zin.repository.Review;

import com.muzin.mu.zin.entity.Review.ReviewKeyword;
import com.muzin.mu.zin.entity.Review.ReviewKeywordMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface ReviewKeywordRepository extends JpaRepository<ReviewKeyword, Long> {

    // 활성화된 키워드 전체 조회
    List<ReviewKeyword> findAllByIsActiveTrueOrderByDisplayOrderAsc();

    // 사용자가 선택한 키워드 목록 중 실제 존재하고 활성화된 키워드만 조회
    List<ReviewKeyword> findAllByReviewKeywordIdInAndIsActiveTrue(List<Long> keywordIds);
}
