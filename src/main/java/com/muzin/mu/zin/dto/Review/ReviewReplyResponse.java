package com.muzin.mu.zin.dto.Review;

import java.time.LocalDateTime;

public record ReviewReplyResponse(
        Long reviewReplyId,
        Long artistProfileId,
        String artistName,
        String content,
        LocalDateTime createDt,
        LocalDateTime updateDt
) {
}
