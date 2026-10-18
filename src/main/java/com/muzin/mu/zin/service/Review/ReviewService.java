package com.muzin.mu.zin.service.Review;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.Review.*;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.Review.Review;
import com.muzin.mu.zin.entity.Review.ReviewKeyword;
import com.muzin.mu.zin.entity.Review.ReviewKeywordMap;
import com.muzin.mu.zin.entity.Review.ReviewReply;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.notification.NotificationRefType;
import com.muzin.mu.zin.entity.notification.NotificationType;
import com.muzin.mu.zin.entity.reservation.LessonReservation;
import com.muzin.mu.zin.entity.reservation.ReservationStatus;
import com.muzin.mu.zin.event.NotificationEvent;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.Review.ReviewKeywordMapRepository;
import com.muzin.mu.zin.repository.Review.ReviewKeywordRepository;
import com.muzin.mu.zin.repository.Review.ReviewReplyRepository;
import com.muzin.mu.zin.repository.Review.ReviewRepository;
import com.muzin.mu.zin.repository.artist.ArtistProfileRepository;
import com.muzin.mu.zin.repository.lesson.LessonReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * 리뷰 정책
 *  - 실제로 레슨을 받은 사람만: 본인 예약 + 완료(COMPLETED) + 완료 후 14일 안
 *  - 예약당 리뷰 1개 (삭제한 리뷰도 1개로 셈 -> 지우고 다시 쓰기 불가)
 *  - 별점 1~5 필수, 태그 최대 3개, 내용 500자 (선택)
 *  - 작성자는 작성 후 7일 안에 수정 가능, 삭제는 언제든
 *  - 아티스트는 리뷰당 답글 1개 (수정/삭제 가능, 지운 뒤 다시 쓰면 같은 답글을 되살림)
 *  - 알림: 리뷰 작성 -> 아티스트, 첫 답글 -> 리뷰 작성자
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    public static final int WRITE_DAYS = 14;
    public static final int EDIT_DAYS = 7;
    private static final int MAX_KEYWORDS = 3;

    private final ReviewRepository reviewRepository;
    private final ReviewKeywordRepository reviewKeywordRepository;
    private final ReviewKeywordMapRepository reviewKeywordMapRepository;
    private final ReviewReplyRepository reviewReplyRepository;
    private final LessonReservationRepository lessonReservationRepository;
    private final ArtistProfileRepository artistProfileRepository;
    private final ApplicationEventPublisher publisher;

    // 리뷰 키워드 목록 조회
    public List<ReviewKeywordResponse> getReviewKeywords() {
        return reviewKeywordRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toKeywordResp)
                .toList();
    }

    // ---------- 리뷰 작성 / 수정 / 삭제 ----------

    @Transactional
    public Long createReview(Long loginUserId, ReviewCreateRequest req) {
        LessonReservation reservation = lessonReservationRepository.findById(req.reservationId())
                .orElseThrow(() -> ApiException.notFound("예약을 찾을 수 없어요."));

        validateReviewWritable(loginUserId, reservation);

        // 삭제한 리뷰 포함 - 예약당 1번만
        if (reviewRepository.existsByReservation_ReservationId(reservation.getReservationId())) {
            throw ApiException.conflict("REVIEW_ALREADY_EXISTS", "이 레슨에는 이미 리뷰를 썼어요. (삭제한 리뷰는 다시 쓸 수 없어요)");
        }

        List<ReviewKeyword> keywords = validateAndGetKeywords(req.keywordIds());

        Lesson lesson = reservation.getLesson();
        ArtistProfile artistProfile = lesson.getArtistProfile();

        Review saved = reviewRepository.save(Review.builder()
                .reservation(reservation)
                .lesson(lesson)
                .artistProfile(artistProfile)
                .reviewUser(reservation.getUser())
                .rating(req.rating())
                .content(normalizeContent(req.content()))
                .build());

        saveReviewKeywordMaps(saved, keywords);

        publisher.publishEvent(new NotificationEvent(
                artistProfile.getUser().getUserId(),
                NotificationType.REVIEW_CREATED,
                "새 리뷰",
                limit("\"" + lesson.getTitle() + "\" 레슨에 ★" + req.rating() + " 리뷰가 달렸어요."),
                NotificationRefType.REVIEW,
                saved.getReviewId()
        ));

        return saved.getReviewId();
    }

    @Transactional
    public Long updateReview(Long loginUserId, Long reviewId, ReviewUpdateRequest req) {
        Review review = findActiveReview(reviewId);

        if (!Objects.equals(review.getReviewUser().getUserId(), loginUserId)) {
            throw ApiException.forbidden("REVIEW_FORBIDDEN", "내가 쓴 리뷰만 수정할 수 있어요.");
        }
        if (editableUntil(review).isBefore(TimeDefaults.nowKst())) {
            throw ApiException.conflict("REVIEW_EDIT_EXPIRED", "리뷰는 작성 후 " + EDIT_DAYS + "일 안에만 수정할 수 있어요.");
        }

        List<ReviewKeyword> keywords = validateAndGetKeywords(req.keywordIds());

        review.update(req.rating(), normalizeContent(req.content()));

        // 기존 키워드 매핑 교체
        reviewKeywordMapRepository.deleteAllByReview_ReviewId(reviewId);
        reviewKeywordMapRepository.flush();
        saveReviewKeywordMaps(review, keywords);

        return review.getReviewId();
    }

    // 리뷰 삭제(soft delete) - 답글도 같이 숨김
    @Transactional
    public void deleteReview(Long loginUserId, Long reviewId) {
        Review review = findActiveReview(reviewId);

        if (!Objects.equals(review.getReviewUser().getUserId(), loginUserId)) {
            throw ApiException.forbidden("REVIEW_FORBIDDEN", "내가 쓴 리뷰만 삭제할 수 있어요.");
        }

        review.softDelete();
        reviewReplyRepository.findByReview_ReviewIdAndDeleteDtIsNull(reviewId)
                .ifPresent(ReviewReply::softDelete);
    }

    // ---------- 조회 ----------

    // 내가 쓴 리뷰
    public List<ReviewResponse> getMyReviews(Long loginUserId) {
        return toResponses(reviewRepository.findMyReviews(loginUserId));
    }

    // 아직 리뷰를 쓰지 않은 완료 레슨 (작성 기간 안)
    public List<WritableReviewResponse> getWritableReviews(Long loginUserId) {
        LocalDateTime since = TimeDefaults.nowKst().minusDays(WRITE_DAYS);
        return lessonReservationRepository.findReviewWritable(loginUserId, ReservationStatus.COMPLETED, since)
                .stream()
                .map(r -> new WritableReviewResponse(
                        r.getReservationId(),
                        r.getLesson().getLessonId(),
                        r.getLesson().getTitle(),
                        r.getLesson().getArtistProfile().getArtistProfileId(),
                        r.getLesson().getArtistProfile().getUser().getUsername(),
                        r.getTimeSlot().getStartDt(),
                        r.getTimeSlot().getEndDt(),
                        r.getCompletedDt(),
                        r.getCompletedDt().plusDays(WRITE_DAYS)
                ))
                .toList();
    }

    // 레슨 리뷰 모음 (요약 + 목록)
    public ReviewListResponse getLessonReviews(Long lessonId) {
        return toListResponse(reviewRepository.findLessonReviews(lessonId));
    }

    // 아티스트가 받은 전체 리뷰 모음 (요약 + 목록, 리뷰마다 어떤 레슨인지 포함)
    public ReviewListResponse getArtistReviews(Long artistProfileId) {
        return toListResponse(reviewRepository.findArtistReviews(artistProfileId));
    }

    // 내가(아티스트) 받은 리뷰 - 마이페이지 답글 관리용
    public ReviewListResponse getReceivedReviews(Long loginUserId) {
        ArtistProfile profile = artistProfileRepository.findByUser_UserId(loginUserId)
                .orElseThrow(() -> ApiException.forbidden("ARTIST_PROFILE_REQUIRED", "아티스트만 받은 리뷰를 볼 수 있어요."));
        return toListResponse(reviewRepository.findArtistReviews(profile.getArtistProfileId()));
    }

    // ---------- 아티스트 답글 ----------

    @Transactional
    public Long createReviewReply(Long loginUserId, ReviewReplyCreateRequest req) {
        Review review = findActiveReview(req.reviewId());
        ArtistProfile artistProfile = review.getArtistProfile();
        validateArtistOwner(loginUserId, artistProfile);

        String content = requireContent(req.content());
        Optional<ReviewReply> existing = reviewReplyRepository.findByReview_ReviewId(review.getReviewId());

        if (existing.isPresent()) {
            ReviewReply reply = existing.get();
            if (!reply.isDeleted()) {
                throw ApiException.conflict("REVIEW_REPLY_EXISTS", "답글은 리뷰당 한 번만 달 수 있어요. 기존 답글을 수정해주세요.");
            }
            // 지웠던 답글 다시 쓰기 (알림은 처음 한 번만)
            reply.restore(content);
            return reply.getReviewReplyId();
        }

        ReviewReply saved = reviewReplyRepository.save(ReviewReply.builder()
                .review(review)
                .artistProfile(artistProfile)
                .content(content)
                .build());

        publisher.publishEvent(new NotificationEvent(
                review.getReviewUser().getUserId(),
                NotificationType.REVIEW_REPLIED,
                "리뷰 답글",
                limit(artistProfile.getUser().getUsername() + "님이 \"" + review.getLesson().getTitle() + "\" 리뷰에 답글을 남겼어요."),
                NotificationRefType.REVIEW,
                review.getReviewId()
        ));

        return saved.getReviewReplyId();
    }

    @Transactional
    public Long updateReviewReply(Long loginUserId, Long reviewReplyId, ReviewReplyUpdateRequest req) {
        ReviewReply reply = reviewReplyRepository.findByReviewReplyIdAndDeleteDtIsNull(reviewReplyId)
                .orElseThrow(() -> ApiException.notFound("답글을 찾을 수 없어요."));

        validateArtistOwner(loginUserId, reply.getArtistProfile());
        reply.update(requireContent(req.content()));

        return reply.getReviewReplyId();
    }

    // 답글 삭제 = soft delete
    @Transactional
    public void deleteReviewReply(Long loginUserId, Long reviewReplyId) {
        ReviewReply reply = reviewReplyRepository.findByReviewReplyIdAndDeleteDtIsNull(reviewReplyId)
                .orElseThrow(() -> ApiException.notFound("답글을 찾을 수 없어요."));

        validateArtistOwner(loginUserId, reply.getArtistProfile());
        reply.softDelete();
    }

    // ---------- 검증 ----------

    private Review findActiveReview(Long reviewId) {
        return reviewRepository.findByReviewIdAndDeleteDtIsNull(reviewId)
                .orElseThrow(() -> ApiException.notFound("리뷰를 찾을 수 없어요."));
    }

    // 실제로 레슨을 받은 사람만 (본인 예약 + 완료 + 기간 안)
    private void validateReviewWritable(Long loginUserId, LessonReservation reservation) {
        if (!Objects.equals(reservation.getUser().getUserId(), loginUserId)) {
            throw ApiException.forbidden("REVIEW_FORBIDDEN", "내가 받은 레슨에만 리뷰를 쓸 수 있어요.");
        }
        if (reservation.getStatus() != ReservationStatus.COMPLETED || reservation.getCompletedDt() == null) {
            throw ApiException.conflict("REVIEW_NOT_WRITABLE", "레슨이 완료된 뒤에 리뷰를 쓸 수 있어요.");
        }
        if (reservation.getCompletedDt().plusDays(WRITE_DAYS).isBefore(TimeDefaults.nowKst())) {
            throw ApiException.conflict("REVIEW_PERIOD_EXPIRED", "리뷰는 레슨 완료 후 " + WRITE_DAYS + "일 안에 쓸 수 있어요.");
        }
    }

    private List<ReviewKeyword> validateAndGetKeywords(List<Long> keywordIds) {
        List<Long> ids = keywordIds == null ? List.of() : keywordIds.stream().filter(Objects::nonNull).distinct().toList();

        if (ids.size() > MAX_KEYWORDS) {
            throw ApiException.badRequest("태그는 최대 " + MAX_KEYWORDS + "개까지 고를 수 있어요.");
        }
        if (ids.isEmpty()) {
            return List.of();
        }

        List<ReviewKeyword> keywords = reviewKeywordRepository.findAllByReviewKeywordIdInAndIsActiveTrue(ids);
        if (keywords.size() != ids.size()) {
            throw ApiException.badRequest("고를 수 없는 태그가 포함되어 있어요.");
        }
        return keywords;
    }

    private void validateArtistOwner(Long loginUserId, ArtistProfile artistProfile) {
        if (!Objects.equals(artistProfile.getUser().getUserId(), loginUserId)) {
            throw ApiException.forbidden("REVIEW_REPLY_FORBIDDEN", "내 레슨에 달린 리뷰에만 답글을 쓸 수 있어요.");
        }
    }

    private void saveReviewKeywordMaps(Review review, List<ReviewKeyword> keywords) {
        if (keywords.isEmpty()) {
            return;
        }
        reviewKeywordMapRepository.saveAll(keywords.stream()
                .map(keyword -> ReviewKeywordMap.builder().review(review).reviewKeyword(keyword).build())
                .toList());
    }

    private String normalizeContent(String content) {
        return content == null || content.isBlank() ? null : content.trim();
    }

    private String requireContent(String content) {
        String trimmed = normalizeContent(content);
        if (trimmed == null) {
            throw ApiException.badRequest("답글 내용을 입력해주세요.");
        }
        return trimmed;
    }

    // 알림 내용 255자 제한
    private static String limit(String text) {
        return text.length() <= 255 ? text : text.substring(0, 254) + "…";
    }

    private static LocalDateTime editableUntil(Review review) {
        return review.getCreateDt().plusDays(EDIT_DAYS);
    }

    // ---------- 응답 만들기 (태그·답글은 리뷰 수와 관계없이 쿼리 1번씩) ----------

    private ReviewListResponse toListResponse(List<Review> reviews) {
        List<ReviewResponse> responses = toResponses(reviews);
        return new ReviewListResponse(summarize(responses), responses);
    }

    private List<ReviewResponse> toResponses(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return List.of();
        }
        List<Long> ids = reviews.stream().map(Review::getReviewId).toList();

        Map<Long, List<ReviewKeywordResponse>> keywordsByReview = reviewKeywordMapRepository.findAllWithKeywordByReviewIds(ids)
                .stream()
                .collect(Collectors.groupingBy(
                        m -> m.getReview().getReviewId(),
                        Collectors.mapping(m -> toKeywordResp(m.getReviewKeyword()), Collectors.toList())));

        Map<Long, ReviewReply> replyByReview = reviewReplyRepository.findAllByReview_ReviewIdInAndDeleteDtIsNull(ids)
                .stream()
                .collect(Collectors.toMap(r -> r.getReview().getReviewId(), Function.identity()));

        return reviews.stream().map(review -> {
            List<ReviewKeywordResponse> keywords = new ArrayList<>(keywordsByReview.getOrDefault(review.getReviewId(), List.of()));
            keywords.sort(Comparator.comparing(ReviewKeywordResponse::displayOrder));
            ReviewReply reply = replyByReview.get(review.getReviewId());

            return new ReviewResponse(
                    review.getReviewId(),
                    review.getReservation().getReservationId(),
                    review.getLesson().getLessonId(),
                    review.getLesson().getTitle(),
                    review.getArtistProfile().getArtistProfileId(),
                    review.getArtistProfile().getUser().getUsername(),
                    review.getReviewUser().getUserId(),
                    review.getReviewUser().getUsername(),
                    review.getReviewUser().getProfileImgUrl(),
                    review.getRating(),
                    review.getContent(),
                    keywords,
                    reply == null ? null : toReviewReplyResp(reply),
                    review.getReservation().getTimeSlot().getStartDt(),
                    review.getCreateDt(),
                    review.getUpdateDt(),
                    editableUntil(review)
            );
        }).toList();
    }

    // 평균 별점, 별점별 개수, 많이 선택된 태그 순위
    private ReviewSummaryResponse summarize(List<ReviewResponse> reviews) {
        long count = reviews.size();
        double average = count == 0 ? 0 : Math.round(reviews.stream().mapToInt(ReviewResponse::rating).average().orElse(0) * 10) / 10.0;

        List<Long> ratingCounts = new ArrayList<>();
        for (int star = 5; star >= 1; star--) {
            final int s = star;
            ratingCounts.add(reviews.stream().filter(r -> r.rating() == s).count());
        }

        Map<Long, ReviewKeywordResponse> keywordById = new HashMap<>();
        Map<Long, Long> keywordCounts = new HashMap<>();
        reviews.forEach(r -> r.keywords().forEach(k -> {
            keywordById.putIfAbsent(k.reviewKeywordId(), k);
            keywordCounts.merge(k.reviewKeywordId(), 1L, Long::sum);
        }));

        List<ReviewKeywordCount> topKeywords = keywordCounts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Long, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(e -> keywordById.get(e.getKey()).displayOrder()))
                .map(e -> new ReviewKeywordCount(e.getKey(), keywordById.get(e.getKey()).keywordName(), e.getValue()))
                .toList();

        return new ReviewSummaryResponse(count, average, ratingCounts, topKeywords);
    }

    private ReviewKeywordResponse toKeywordResp(ReviewKeyword keyword) {
        return new ReviewKeywordResponse(keyword.getReviewKeywordId(), keyword.getKeywordName(), keyword.getDisplayOrder());
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
