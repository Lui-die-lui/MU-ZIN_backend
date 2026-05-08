package com.muzin.mu.zin.repository.Review;

import com.muzin.mu.zin.entity.Review.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// 유저들 리뷰
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Soft delete 됐는지 / 아닌지
    boolean existsByReservation_ReservationIdAndDeleteDtIsNull(Long reservationId);

    // 유효한 리뷰만 보여줌
    Optional<Review> findByReviewIdAndDeleteDtIsNull(Long reviewId);
}
