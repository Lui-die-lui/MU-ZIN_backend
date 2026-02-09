package com.muzin.mu.zin.dto.reservation;

import com.muzin.mu.zin.dto.lesson.TimeSlotResponse;
import com.muzin.mu.zin.entity.reservation.ReservationStatus;

import java.time.LocalDateTime;

public record ArtistReservationSummaryResponse(
        Long reservationId,
        ReservationStatus status,
        Integer priceAtBooking,
        LocalDateTime requestedDt,
        LocalDateTime confirmedDt,
        LocalDateTime canceledDt,
        Long lessonId,
        String lessonTitle,        // 카드에 필요
        TimeSlotResponse timeSlot, // startDt 표시용
        Long requesterUserId,
        String requesterUsername,  // 카드에 필요
        Boolean hasMessage         // 메시지 개념 있으면
) {
}
