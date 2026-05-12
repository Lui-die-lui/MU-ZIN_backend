package com.muzin.mu.zin.service.Review;

import com.muzin.mu.zin.dto.Review.*;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.Review.Review;
import com.muzin.mu.zin.entity.Review.ReviewKeyword;
import com.muzin.mu.zin.entity.Review.ReviewKeywordMap;
import com.muzin.mu.zin.entity.Review.ReviewReply;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.reservation.LessonReservation;
import com.muzin.mu.zin.entity.reservation.ReservationStatus;
import com.muzin.mu.zin.repository.Review.ReviewKeywordMapRepository;
import com.muzin.mu.zin.repository.Review.ReviewKeywordRepository;
import com.muzin.mu.zin.repository.Review.ReviewReplyRepository;
import com.muzin.mu.zin.repository.Review.ReviewRepository;
import com.muzin.mu.zin.repository.lesson.LessonReservationRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.boot.model.naming.IllegalIdentifierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

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

    // 리뷰 작성
    @Transactional
    public Long createReview(Long loginUserId, ReviewCreateRequest req) {
        LessonReservation reservation = lessonReservationRepository.findById(req.reservationId())
                .orElseThrow(() -> new IllegalArgumentException("예약 정보를 찾을 수 없습니다."));

        validateReviewWritable(loginUserId, reservation);

        boolean alreadyExists = reviewRepository
                .existsByReservation_ReservationIdAndDeleteDtIsNull(req.reservationId());

        if (alreadyExists) {
            throw new IllegalArgumentException("이미 해당 예약에 대한 리뷰를 작성했습니다.");
        }

        List<ReviewKeyword> keywords = validateAndGetKeywords(req.keywordIds());

        Lesson lesson = extractLessonFromReservation(reservation);
        ArtistProfile artistProfile = lesson.getArtistProfile();

        Review review = Review.builder()
                .reservation(reservation)
                .lesson(lesson)
                .artistProfile(artistProfile)
                .reviewUser(reservation.getUser())
                .rating(req.rating())
                .content(normalizeContent(req.content()))
                .build();

        Review saveReview = reviewRepository.save(review);

        saveReviewKeywordMaps(saveReview, keywords);

        return saveReview.getReviewId();
    }

    // 리뷰 수정
    @Transactional
    public Long updateReview(Long loginUserId, Long reviewId, ReviewUpdateRequest req) {
        Review review = reviewRepository.findByReviewIdAndDeleteDtIsNull(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));

        if (!Objects.equals(review.getReviewUser().getUserId(), loginUserId)) {
            throw new IllegalArgumentException("본인이 작성한 리뷰만 수정할 수 있습니다.");
        }

        // 정책 선택 사항 - 작성 후 7일 이내 수정 가능하게 할 거면 아래 검증 사용
        validateReviewEditable(review);

        List<ReviewKeyword> keywords = validateAndGetKeywords(req.keywordIds());

        review.update(req.rating(), normalizeContent(req.content()));

        // 기존 키워드 매핑 삭제
        reviewKeywordMapRepository.deleteAllByReview_ReviewId(reviewId);
        reviewKeywordMapRepository.flush();

        // 새 키워드 매핑 저장
        saveReviewKeywordMaps(review, keywords);

        return review.getReviewId();
    }

    // 리뷰 삭제(soft delete)
    @Transactional
    public void deleteReview(Long loginUserId, Long reviewId) {
        Review review = reviewRepository.findByReviewIdAndDeleteDtIsNull(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));

        if (!Objects.equals(review.getReviewUser().getUserId(), loginUserId)) {
            throw new IllegalArgumentException("본인이 작성한 리뷰만 삭제할 수 있습니다.");
        }

        review.softDelete();

        reviewReplyRepository.findByReview_ReviewIdAndDeleteDtIsNull(reviewId)
                .ifPresent(ReviewReply::softDelete);
    }

    // 내가 작성한 리뷰 목록
    public List<ReviewResponse> getMyReviews(Long loginUserId) {
        return reviewRepository.findAllByReviewUser_UserIdAndDeleteDtIsNullOrderByCreateDtDesc(loginUserId)
                .stream()
                .map(this::toReviewResp)
                .toList();
    }

    // 특정 레슨의 리뷰 목록
    public List<ReviewResponse> getLessonReviews(Long lessonId) {
        return reviewRepository.findAllByLesson_LessonIdAndDeleteDtIsNullOrderByCreateDtDesc(lessonId)
                .stream()
                .map(this::toReviewResp)
                .toList();
    }

    // 특정 아티스트가 받은 리뷰 목록
    public List<ReviewResponse> getArtistReviews(Long artistProfileId) {
        return reviewRepository.findAllByArtistProfile_ArtistProfileIdAndDeleteDtIsNullOrderByCreateDtDesc(artistProfileId)
                .stream()
                .map(this::toReviewResp)
                .toList();
    }

    // 리뷰 답글 작성(아티스트)
    public Long createReviewReply(Long loginUserId, ReviewReplyCreateRequest req) {
        Review review = reviewRepository.findByReviewIdAndDeleteDtIsNull(req.reviewId())
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));

        ArtistProfile artistProfile = review.getArtistProfile();

        validateArtistOwner(loginUserId, artistProfile);

        boolean alreadyExists = reviewReplyRepository
                .existsByReview_ReviewIdAndDeleteDtIsNull(req.reviewId());

        if (alreadyExists) {
            throw new IllegalArgumentException("이미 해당 리뷰에 답글을 작성했습니다.");
        }

        ReviewReply reply = ReviewReply.builder()
                .review(review)
                .artistProfile(artistProfile)
                .content(req.content())
                .build();

        ReviewReply saveReply = reviewReplyRepository.save(reply);

        return saveReply.getReviewReplyId();
    }


    // 리뷰 키워드 반환타입
    private ReviewKeywordResponse toKeywordResp(ReviewKeyword keyword) {
        return new ReviewKeywordResponse(
                keyword.getReviewKeywordId(),
                keyword.getKeywordName(),
                keyword.getDisplayOrder()
        );
    }

    // 키워드 검증 및 조회
    private List<ReviewKeyword> validateAndGetKeywords(List<Long> keywordIds) {
        List<Long> normalizedKeywordIds = keywordIds == null
                ? List.of()
                : keywordIds.stream()
                .distinct()
                .toList();

        if (normalizedKeywordIds.size() > 3) {
            throw new IllegalIdentifierException("리뷰 키워드는 최대 3개까지 선택할 수 있습니다.");
        }

        if (normalizedKeywordIds.isEmpty()) {
            return List.of();
        }

        List<ReviewKeyword> keywords = reviewKeywordRepository
                .findAllByReviewKeywordIdInAndIsActiveTrue(normalizedKeywordIds);

        if (keywords.size() != normalizedKeywordIds.size()) {
            throw new IllegalArgumentException("존재하지 않거나 비활성화된 리뷰 키워드가 포함되어 있습니다.");
        }

        return keywords;
    }

    //

    // 리뷰 작성 가능 예약 검증
    private void validateReviewWritable(Long loginUserId, LessonReservation reservation) {
        if (!Objects.equals(reservation.getUser().getUserId(), loginUserId)) {
            throw new IllegalArgumentException("본인의 예약에만 리뷰를 작성할 수 있습니다.");
        }

        if (reservation.getStatus() != ReservationStatus.COMPLETED) {
            throw new IllegalArgumentException("완료된 레슨에만 리뷰를 작성할 수 있습니다.");
        }

        LocalDateTime completedDt = resolveCompletedDt(reservation);

        if (completedDt == null) {
            throw new IllegalArgumentException("레슨 완료 시간이 없어 리뷰를 작성할 수 없습니다.");
        }

        if (completedDt.plusDays(14).isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("리뷰 작성 가능 기간이 지났습니다.");
        }
    }

    // 리뷰 수정 가능 기간 검증
    private void validateReviewEditable(Review review) {
        if (review.getCreateDt().plusDays(7).isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("리뷰 수정 가능 기간이 지났습니다.");
        }
    }

    // 리뷰 - 키워드 매핑 저장
    private void saveReviewKeywordMaps(Review review, List<ReviewKeyword> keywords) {
        if (keywords.isEmpty()) {
            return;
        }

        List<ReviewKeywordMap> maps = keywords.stream()
                .map(keyword -> ReviewKeywordMap.builder()
                        .review(review)
                        .reviewKeyword(keyword)
                        .build())
                .toList();

        reviewKeywordMapRepository.saveAll(maps);
    }

    // 아티스트 본인 검증
    private void validateArtistOwner(Long loginUserId, ArtistProfile artistProfile) {
        if (!Objects.equals(artistProfile.getUser().getUserId(), loginUserId)) {
            throw new IllegalArgumentException("해당 아티스트만 답글을 작성하거나 수정할 수 있습니다.");
        }
    }


    // TODO: 현재 아래 부분에서 확인 - 예약이 레슨을 가지고 있는지(아니면 TimeSlot에서 꺼내야함)
    // 완료일시 꺼내 쓰기
    private LocalDateTime resolveCompletedDt(LessonReservation reservation) {
        return reservation.getCompletedDt();
    }

    // reservation - 레슨 꺼내기
    private Lesson extractLessonFromReservation(LessonReservation reservation) {
        return reservation.getLesson();
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        return content.trim();
    }

    // 리뷰 반환
    private ReviewResponse toReviewResp(Review review) {

        // 키워드
        List<ReviewKeywordResponse> keywords = reviewKeywordMapRepository
                .findAllByReview_ReviewId(review.getReviewId())
                .stream()
                .map(ReviewKeywordMap::getReviewKeyword)
                .map(this::toKeywordResp)
                .toList();

        ReviewReplyResponse reply = reviewReplyRepository
                .findByReview_ReviewIdAndDeleteDtIsNull(review.getReviewId())
                .map(this::toReviewReplyResp)
                .orElse(null);

        return new ReviewResponse(
                review.getReviewId(),
                review.getReservation().getReservationId(),
                review.getLesson().getLessonId(),
                review.getLesson().getTitle(),
                review.getArtistProfile().getArtistProfileId(),
                review.getArtistProfile().getUser().getUsername(),
                review.getReviewUser().getUserId(),
                review.getReviewUser().getUsername(),
                review.getRating(),
                review.getContent(),
                keywords,
                reply,
                review.getCreateDt(),
                review.getUpdateDt()
        );
    }

    private ReviewReplyResponse toReviewReplyResp(ReviewReply reply) {
        return new ReviewReplyResponse(
                reply.getReviewReplyId(),
                reply.getArtistProfile().getArtistProfileId(),
                reply.getArtistProfile().getUser().getUsername(),
                reply.getContent(),
                reply.getCreateDt(),
                reply.getUpdateDt()
        );
    }
}
