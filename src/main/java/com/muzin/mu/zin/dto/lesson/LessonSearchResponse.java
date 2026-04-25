package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.dto.instrument.InstrumentResponse;
import com.muzin.mu.zin.dto.region.SearchMainRegionSummary;
import com.muzin.mu.zin.entity.instrument.InstrumentCategory;
import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonStatus;

import java.time.LocalDateTime;

public record LessonSearchResponse(
        Long lessonId,
        String title,
        String description,
        Integer price,
        Integer durationMin,
        LessonMode mode,
        LessonStatus status,

        // 검색 시 카드에 악기 정보 추가
        Long instId,
        String instName,
        InstrumentCategory instCategory,

        // 카드 표시용 아티스트 대표 지역
       SearchMainRegionSummary mainRegionSummary
) {
}
