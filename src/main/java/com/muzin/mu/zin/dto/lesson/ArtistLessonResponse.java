package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.dto.artist.ArtistProfileResponse;
import com.muzin.mu.zin.entity.lesson.LessonClosingPolicy;
import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ArtistLessonResponse(
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
        LessonClosingPolicy closingPolicy,
        LocalDateTime createDt,
        LocalDateTime updateDt,
        String instName,
        // 진행 중인 예약(요청 대기/확정/완료 대기) 수 - 0 보다 크면 핵심 정보 수정·레슨 삭제 불가
        long activeReservationCount,
        boolean deleted,
        // 수락/거절을 기다리는 예약 요청 수
        long pendingRequestCount,
        // 앞으로 예약 가능한(OPEN) 시간 수
        long upcomingOpenSlotCount
) {
}
