package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.entity.TimePart;
import com.muzin.mu.zin.entity.instrument.InstrumentCategory;
import com.muzin.mu.zin.entity.lesson.LessonMode;

import java.time.LocalDateTime;
import java.util.List;

// 검색 파라미터 전용 레코드
public record LessonSearchCond(
        String keyword,
        LessonMode mode,
        List<Long> styleTagIds,
        InstrumentCategory instCategory,
        List<Long> instIds,
        LocalDateTime fromDt,
        LocalDateTime toDt,
        List<Integer> daysOfWeek,
        List<TimePart> timeParts
) {
}
