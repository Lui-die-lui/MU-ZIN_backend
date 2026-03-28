package com.muzin.mu.zin.dto.reservation;

import com.muzin.mu.zin.dto.lesson.TimeSlotResponse;
import com.muzin.mu.zin.entity.reservation.CompletionSource;
import com.muzin.mu.zin.entity.reservation.ReservationStatus;

import java.time.LocalDateTime;

public record ArtistReservationDetailResponse(
        Long reservationId,
        ReservationStatus status,
        Integer priceAtBooking,

        LocalDateTime requestedDt,
        LocalDateTime confirmedDt,
        LocalDateTime canceledDt,
        LocalDateTime completionPendingDt,
        LocalDateTime completedDt,
        CompletionSource completionSource,

        Long lessonId,
        String lessonTitle,
        TimeSlotResponse timeSlot,
        Long artistUserId,
        String artistDisplayName,
        Long requesterUserId,
        String requesterUsername,
        String requestMsg,             // 상세에서만

        boolean canMarkCompleted,
        boolean myCompletionConfirmed
) {
}
