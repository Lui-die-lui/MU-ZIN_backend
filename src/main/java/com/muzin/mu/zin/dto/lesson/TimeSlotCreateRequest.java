package com.muzin.mu.zin.dto.lesson;

import java.time.LocalDateTime;
import java.util.List;

public record TimeSlotCreateRequest(
        List<LocalDateTime> startDts
) {}
