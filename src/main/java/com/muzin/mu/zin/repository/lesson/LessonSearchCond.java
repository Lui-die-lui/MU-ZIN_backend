package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.entity.TimePart;
import com.muzin.mu.zin.entity.instrument.InstrumentCategory;
import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonSort;

import java.time.LocalDateTime;
import java.util.List;

// 검색 파라미터 전용 레코드
public record LessonSearchCond(
        String keyword,
        LessonMode mode,
        List<Long> styleTagIds,
        InstrumentCategory instCategory,
        List<Long> instIds,
//        LessonSort sort,

        String region1DepthName,
        String region2DepthName,
        String region3DepthName,

        LocalDateTime fromDt,
        LocalDateTime toDt,
        List<Integer> daysOfWeek,
        List<TimePart> timeParts
) {
}
