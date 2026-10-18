package com.muzin.mu.zin.repository.Review;

import com.muzin.mu.zin.entity.Review.ReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// 아티스트 대댓 리뷰
public interface ReviewReplyRepository extends JpaRepository<ReviewReply, Long> {

    // 삭제된 답글 포함 (리뷰당 1행 - 다시 쓰면 되살림)
    Optional<ReviewReply> findByReview_ReviewId(Long reviewId);

    Optional<ReviewReply> findByReview_ReviewIdAndDeleteDtIsNull(Long reviewId);

    Optional<ReviewReply> findByReviewReplyIdAndDeleteDtIsNull(Long reviewReplyId);

    // 여러 리뷰의 답글을 한 번에
    List<ReviewReply> findAllByReview_ReviewIdInAndDeleteDtIsNull(Collection<Long> reviewIds);
}
