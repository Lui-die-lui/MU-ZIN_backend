package com.muzin.mu.zin.dto.lesson;

import com.muzin.mu.zin.dto.region.SearchMainRegionSummary;
import com.muzin.mu.zin.entity.lesson.LessonMode;

import java.util.List;

// 추천 레슨 카드 (공개 정보만: 승인 아티스트의 공개 레슨)
public record RecommendedLessonResponse(
        Long lessonId,
        String title,
        String description,
        Integer price,
        Integer durationMin,
        LessonMode mode,
        Long instId,
        String instName,
        Long artistProfileId,
        String artistName,
        String artistProfileImgUrl,
        SearchMainRegionSummary artistMainRegion,
        String regionMatch,           // MAIN: 대표 지역이 선택 지역 / SERVICE: 활동 가능 지역에 선택 지역이 있음
        List<StyleTag> styleTags,     // 레슨에 아티스트가 직접 붙인 스타일 태그
        List<Long> matchedTagIds,     // 그중 취향과 맞는 태그
        boolean modeMatched,          // 원하는 수업 방식인지
        int preferenceScore,          // 정렬용 (태그 일치 x2 + 수업 방식 일치 x1). 적합도 확률이 아님
        boolean sample                // 샘플(시연용) 계정 레슨 - 개발 환경에서만 내려옴, 화면에 "샘플" 표시
) {
    public record StyleTag(Long lessonStyleTagId, String styleName) {
    }
}
