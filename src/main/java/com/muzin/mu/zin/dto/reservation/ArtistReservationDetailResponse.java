package com.muzin.mu.zin.dto.reservation;

import com.muzin.mu.zin.dto.lesson.TimeSlotResponse;
import com.muzin.mu.zin.entity.reservation.ReservationStatus;

import java.time.LocalDateTime;

public record ArtistReservationDetailResponse(
        Long reservationId,
        ReservationStatus status,
        Integer priceAtBooking,
        LocalDateTime requestedDt,
        LocalDateTime confirmedDt,
        LocalDateTime canceledDt,
        Long lessonId,
        String lessonTitle,
        TimeSlotResponse timeSlot,
        Long requesterUserId,
        String requesterUsername,
        String content             // 상세에서만
) {
}
