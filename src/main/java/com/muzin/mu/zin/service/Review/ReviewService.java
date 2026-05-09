package com.muzin.mu.zin.service.Review;

import com.muzin.mu.zin.dto.Review.ReviewKeywordResponse;
import com.muzin.mu.zin.entity.Review.ReviewKeyword;
import com.muzin.mu.zin.repository.Review.ReviewKeywordMapRepository;
import com.muzin.mu.zin.repository.Review.ReviewKeywordRepository;
import com.muzin.mu.zin.repository.Review.ReviewReplyRepository;
import com.muzin.mu.zin.repository.Review.ReviewRepository;
import com.muzin.mu.zin.repository.lesson.LessonReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewKeywordRepository reviewKeywordRepository;
    private final ReviewKeywordMapRepository reviewKeywordMapRepository;
    private final ReviewReplyRepository reviewReplyRepository;
    private final LessonReservationRepository lessonReservationRepository;

    // 리뷰 키워드 목록 조회
    public List<ReviewKeywordResponse> getReviewKeywords() {
        return reviewKeywordRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toKeywordResp)
                .toList();

    }

    private ReviewKeywordResponse toKeywordResp(ReviewKeyword keyword) {
        return new ReviewKeywordResponse(
                keyword.getReviewKeywordId(),
                keyword.getKeywordName(),
                keyword.getDisplayOrder()
        );
    }
}
