package com.muzin.mu.zin.dto.reservation;

public record ArtistCancelRequest(
        String reason,
        boolean reopenSlot // true면 OPEN, false면 CLOSE
) {
}
