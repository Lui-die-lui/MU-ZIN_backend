package com.muzin.mu.zin.repository.Review;

import com.muzin.mu.zin.entity.Review.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// 유저들 리뷰
// 목록 조회는 레슨/아티스트/작성자/예약을 한 번에 가져옴 (리뷰마다 추가 쿼리 방지)
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 삭제한 리뷰 포함 - 예약당 리뷰는 1번만 (삭제 후 다시 쓰기 불가)
    boolean existsByReservation_ReservationId(Long reservationId);

    // 유효한 리뷰만 보여줌
    Optional<Review> findByReviewIdAndDeleteDtIsNull(Long reviewId);

    String FETCH = """
            select r from Review r
            join fetch r.lesson l
            join fetch r.artistProfile ap
            join fetch ap.user
            join fetch r.reviewUser
            join fetch r.reservation res
            join fetch res.timeSlot
            """;

    @Query(FETCH + " where r.reviewUser.userId = :userId and r.deleteDt is null order by r.createDt desc")
    List<Review> findMyReviews(@Param("userId") Long userId);

    @Query(FETCH + " where l.lessonId = :lessonId and r.deleteDt is null order by r.createDt desc")
    List<Review> findLessonReviews(@Param("lessonId") Long lessonId);

    @Query(FETCH + " where ap.artistProfileId = :artistProfileId and r.deleteDt is null order by r.createDt desc")
    List<Review> findArtistReviews(@Param("artistProfileId") Long artistProfileId);
}
