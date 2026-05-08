package com.muzin.mu.zin.repository.Review;

import com.muzin.mu.zin.entity.Review.ReviewKeywordMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewKeywordMapRepository extends JpaRepository<ReviewKeywordMap, Long> {

    List<ReviewKeywordMap> findAllByReview_ReviewId(Long reviewId);

    void deleteAllByReview_ReviewId(Long reviewId);
}
