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
}
