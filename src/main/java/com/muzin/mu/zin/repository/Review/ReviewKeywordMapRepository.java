package com.muzin.mu.zin.repository.Review;

import com.muzin.mu.zin.entity.Review.ReviewKeywordMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewKeywordMapRepository extends JpaRepository<ReviewKeywordMap, Long> {

    List<ReviewKeywordMap> findAllByReview_ReviewId(Long reviewId);

    // 여러 리뷰의 태그를 한 번에
    @Query("select m from ReviewKeywordMap m join fetch m.reviewKeyword where m.review.reviewId in :reviewIds")
    List<ReviewKeywordMap> findAllWithKeywordByReviewIds(@Param("reviewIds") Collection<Long> reviewIds);

    void deleteAllByReview_ReviewId(Long reviewId);
}
