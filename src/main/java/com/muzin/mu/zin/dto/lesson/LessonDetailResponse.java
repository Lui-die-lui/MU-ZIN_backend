package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.dto.artist.ArtistSummaryResponse;
import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonStatus;

import java.time.LocalDateTime;
import java.util.List;

public record LessonDetailResponse(
        Long lessonId,
        String title,
        Long instId,
        String description,
        String requirementText,
        Integer price,
        Integer durationMin,
        LessonMode mode,
        LessonStatus status,
        List<LessonStyleTagResponse> styleTags,
        LocalDateTime createDt,
        LocalDateTime updateDt,
        ArtistSummaryResponse artist,
        // "시간이 없으면 자동 비공개" 레슨인데 예약 가능한 시간이 없어 검색에서 빠진 상태
        // (검색 결과를 연 뒤 마지막 시간이 마감된 경우 등) -> 화면에서 "현재 예약할 수 없는 레슨" 안내
        boolean autoClosed
) {
}
