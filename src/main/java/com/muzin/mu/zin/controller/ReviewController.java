package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.Review.ReviewKeywordResponse;
import com.muzin.mu.zin.service.Review.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
