package com.muzin.mu.zin.repository.Review;

import com.muzin.mu.zin.entity.Review.ReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// 아티스트 대댓 리뷰
public interface ReviewReplyRepository extends JpaRepository<ReviewReply, Long> {

    boolean existsByReview_ReviewIdAndDeleteDtIsNull(Long reviewId);

    Optional<ReviewReply> findByReview_ReviewIdAndDeleteDtIsNull(Long reviewId);
}
