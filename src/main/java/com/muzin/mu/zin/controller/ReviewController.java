package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.Review.*;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.Review.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 공개: GET /reviews/keywords, /reviews/lessons/**, /reviews/artists/** (SecurityConfig)
@RestController
@RequiredArgsConstructor
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    // 리뷰 태그 목록 (친절해요, 꼼꼼해요 ...)
    @GetMapping("/keywords")
    public ApiRespDto<List<ReviewKeywordResponse>> getReviewKeywords() {
        return ApiRespDto.ok(reviewService.getReviewKeywords());
    }

    // 리뷰 작성 (완료된 내 예약)
    @PostMapping
    public ApiRespDto<Map<String, Long>> createReview(
            @AuthenticationPrincipal PrincipalUser principalUser,
            @Valid @RequestBody ReviewCreateRequest req
    ) {
        Long reviewId = reviewService.createReview(principalUser.getUserId(), req);
        return ApiRespDto.ok("리뷰를 남겼어요.", Map.of("reviewId", reviewId));
    }

    // 리뷰 수정 (작성 후 7일 안)
    @PutMapping("/{reviewId}")
    public ApiRespDto<Map<String, Long>> updateReview(
            @AuthenticationPrincipal PrincipalUser principalUser,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewUpdateRequest req
    ) {
        Long updated = reviewService.updateReview(principalUser.getUserId(), reviewId, req);
        return ApiRespDto.ok("리뷰를 수정했어요.", Map.of("reviewId", updated));
    }

    // 리뷰 삭제
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @AuthenticationPrincipal PrincipalUser principalUser,
            @PathVariable Long reviewId
    ) {
        reviewService.deleteReview(principalUser.getUserId(), reviewId);
        return ResponseEntity.noContent().build();
    }

    // 내가 쓴 리뷰
    @GetMapping("/me")
    public ApiRespDto<List<ReviewResponse>> getMyReviews(@AuthenticationPrincipal PrincipalUser principalUser) {
        return ApiRespDto.ok(reviewService.getMyReviews(principalUser.getUserId()));
    }

    // 리뷰를 쓸 수 있는 완료 레슨
    @GetMapping("/me/writable")
    public ApiRespDto<List<WritableReviewResponse>> getWritableReviews(@AuthenticationPrincipal PrincipalUser principalUser) {
        return ApiRespDto.ok(reviewService.getWritableReviews(principalUser.getUserId()));
    }

    // 내가(아티스트) 받은 리뷰
    @GetMapping("/received")
    public ApiRespDto<ReviewListResponse> getReceivedReviews(@AuthenticationPrincipal PrincipalUser principalUser) {
        return ApiRespDto.ok(reviewService.getReceivedReviews(principalUser.getUserId()));
    }

    // 레슨 리뷰 모음 (요약 + 목록)
    @GetMapping("/lessons/{lessonId}")
    public ApiRespDto<ReviewListResponse> getLessonReviews(@PathVariable Long lessonId) {
        return ApiRespDto.ok(reviewService.getLessonReviews(lessonId));
    }

    // 아티스트가 받은 전체 리뷰 모음 (요약 + 목록)
    @GetMapping("/artists/{artistProfileId}")
    public ApiRespDto<ReviewListResponse> getArtistReviews(@PathVariable Long artistProfileId) {
        return ApiRespDto.ok(reviewService.getArtistReviews(artistProfileId));
    }

    // 리뷰 답글 작성 (아티스트, 리뷰당 1개)
    @PostMapping("/replies")
    public ApiRespDto<Map<String, Long>> createReviewReply(
            @AuthenticationPrincipal PrincipalUser principalUser,
            @Valid @RequestBody ReviewReplyCreateRequest req
    ) {
        Long replyId = reviewService.createReviewReply(principalUser.getUserId(), req);
        return ApiRespDto.ok("답글을 남겼어요.", Map.of("reviewReplyId", replyId));
    }

    // 리뷰 답글 수정 (아티스트)
    @PutMapping("/replies/{reviewReplyId}")
    public ApiRespDto<Map<String, Long>> updateReviewReply(
            @AuthenticationPrincipal PrincipalUser principalUser,
            @PathVariable Long reviewReplyId,
            @Valid @RequestBody ReviewReplyUpdateRequest req
    ) {
        Long updated = reviewService.updateReviewReply(principalUser.getUserId(), reviewReplyId, req);
        return ApiRespDto.ok("답글을 수정했어요.", Map.of("reviewReplyId", updated));
    }

    // 리뷰 답글 삭제 (아티스트) = soft delete
    @DeleteMapping("/replies/{reviewReplyId}")
    public ResponseEntity<Void> deleteReviewReply(
            @AuthenticationPrincipal PrincipalUser principalUser,
            @PathVariable Long reviewReplyId
    ) {
        reviewService.deleteReviewReply(principalUser.getUserId(), reviewReplyId);
        return ResponseEntity.noContent().build();
    }
}
