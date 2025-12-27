package com.muzin.mu.zin.dto.lesson;

import java.util.List;

public record SetLessonStylesRequest(
        List<Long> styleTagIds
) {
}
