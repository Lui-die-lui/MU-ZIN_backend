package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.entity.TimePart;
import com.muzin.mu.zin.entity.instrument.InstrumentCategory;
import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonSort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

public record LessonSearchRequest(
        String keyword,
        LessonMode mode,
        List<Long> styleTagIds,
        InstrumentCategory instCategory,
        List<Long> instIds,
        LessonSort sort,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime from,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime to,

        List<Integer> daysOfWeek,
        List<TimePart> timeParts
) {
}
