package com.muzin.mu.zin.dto.reservation;

public record ReservationCreateRequest(
        Long timeSlotId,
        String requestMsg
) {
}
