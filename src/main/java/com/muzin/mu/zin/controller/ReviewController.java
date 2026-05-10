package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.Review.ReviewCreateRequest;
import com.muzin.mu.zin.dto.Review.ReviewKeywordResponse;
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
}
