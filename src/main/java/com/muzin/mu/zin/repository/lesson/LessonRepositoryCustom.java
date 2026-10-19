package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.dto.lesson.LessonCardRow;
import com.muzin.mu.zin.entity.lesson.Lesson;
import org.springframework.data.domain.Pageable;


import java.util.List;
import java.util.Optional;

public interface LessonRepositoryCustom {

    // 레슨 검색
    List<Lesson> searchPublicLessons(LessonSearchCond cond, Pageable pageable);

    // 레슨 검색 - 레슨 상세
    Optional<Lesson> findPublicDetailByIdDsl(Long lessonId);

    // 조건에 일치하는 아티스트 레슨 카드 목록
    List<LessonCardRow> findArtistLessonCardRowDsl(Long artistProfileId);

    // 조건에 일치하는 아티스트 레슨 디테일
    Optional<Lesson> findArtistLessonDetailDsl(Long artistProfileId, Long lessonId);

    // 공개 레슨 중 주어진 id 만 (searchPublicLessons 와 같은 공개 조건: 삭제 안 됨·ACTIVE·승인 아티스트·자동 비공개 아님)
    // AI 내비게이터가 모델이 고른 id 를 다시 검증할 때 사용
    List<Lesson> findPublicLessonsByIds(java.util.Collection<Long> lessonIds);
}
