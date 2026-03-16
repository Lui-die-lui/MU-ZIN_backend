package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.dto.artist.ArtistInstrumentSummary;
import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonStatus;

import java.util.List;

// 아티스트 상세 페이지 레슨 카드에 보여줄 조회 구조 분리(나중에 SearchResp 쪽도 리팩토링 고려)
public record LessonCardRow(
        Long lessonId,
        String title,
        Integer price,
        Integer durationMin,
        LessonMode mode,
        Long instId,
        String instName
) {
}
