package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.entity.lesson.LessonMode;

import java.util.List;

// 음악 취미 찾기 -> 레슨 추천
// 필수 조건: instIds(추천 취미의 악기) + 지역(시·도 필수, 시·군·구/읍·면·동 선택)
// 선호 조건(정렬에만 사용, 결과를 거르지 않음): preferTagIds(레슨 스타일 태그), preferModes(수업 방식)
public record LessonRecommendRequest(
        List<Long> instIds,
        String region1DepthName,
        String region2DepthName,
        String region3DepthName,
        List<Long> preferTagIds,
        List<LessonMode> preferModes,
        Integer size
) {
}
