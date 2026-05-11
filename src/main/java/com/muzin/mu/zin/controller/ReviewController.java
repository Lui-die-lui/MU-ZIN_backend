package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.Review.ReviewCreateRequest;
import com.muzin.mu.zin.dto.Review.ReviewKeywordResponse;
import com.muzin.mu.zin.dto.Review.ReviewResponse;
import com.muzin.mu.zin.dto.Review.ReviewUpdateRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.Review.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    // 리뷰 키워드 목록 조회
    @GetMapping("/keywords")
    public ResponseEntity<List<ReviewKeywordResponse>> getReviewKeywords() {
        List<ReviewKeywordResponse> keywords = reviewService.getReviewKeywords();
        return ResponseEntity.ok(keywords);
    }

    // 리뷰 작성
    @PostMapping
    public ResponseEntity<Map<String, Long>> createReview(
            @AuthenticationPrincipal PrincipalUser principalUser,
            @Valid @RequestBody ReviewCreateRequest req
            ) {
        Long loginUserId = principalUser.getUserId();

        Long reviewId = reviewId = reviewService.createReview(loginUserId, req);

        return ResponseEntity.ok(Map.of("reviewId", reviewId));
    }

    // 리뷰 수정
    @PutMapping("/{reviewId}")
    public ResponseEntity<Map<String, Long>> updateReview(
            @AuthenticationPrincipal PrincipalUser principalUser,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewUpdateRequest req
            ) {
        Long loginUserId = principalUser.getUserId();

        Long updateReviewId = reviewService.updateReview(loginUserId, reviewId, req);

        return ResponseEntity.ok(Map.of("reviewId", updateReviewId));
    }

    // 리뷰 삭제
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @AuthenticationPrincipal PrincipalUser principalUser,
            @PathVariable Long reviewId
    ) {
        Long loginUserId = principalUser.getUserId();

        reviewService.deleteReview(loginUserId, reviewId);

        return ResponseEntity.noContent().build();
    }

    // 내가 작성한 리뷰 목록 조회
    @GetMapping("/me")
    public ResponseEntity<List<ReviewResponse>> getMyReviews(
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        Long loginUserId = principalUser.getUserId();

        List<ReviewResponse> reviews = reviewService.getLessonReviews(loginUserId);

        return ResponseEntity.ok(reviews);
    }

    // 특정 레슨 리뷰 목록 조회
    @GetMapping("/lessons/{lessonId}")
    public ResponseEntity<List<ReviewResponse>> getLessonReviews(
            @PathVariable Long lessonId
    ) {
        List<ReviewResponse> reviews = reviewService.getLessonReviews(lessonId);

        return ResponseEntity.ok(reviews);
    }

    // 특정 아티스트가 받은 리뷰 목록 조회
    @GetMapping("/artists/{artistProfileId}")
    public ResponseEntity<List<ReviewResponse>> getArtistReviews(
            @PathVariable Long artistProfileId
    ) {
        List<ReviewResponse> reviews = reviewService.getArtistReviews(artistProfileId);

        return ResponseEntity.ok(reviews);
    }
}
