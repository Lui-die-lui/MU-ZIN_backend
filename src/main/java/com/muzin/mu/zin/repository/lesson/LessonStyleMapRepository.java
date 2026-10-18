package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.entity.lesson.LessonStyleMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LessonStyleMapRepository extends JpaRepository<LessonStyleMap, Long> {

    // 아티스트가 지정해놓은 레슨 스타일 제거
    void deleteByLesson_LessonId(Long lessonId);

    List<LessonStyleMap> findAllByLesson_LessonId(Long lessonId);

    // 여러 레슨의 스타일 태그 한 번에 (레슨 추천)
    @org.springframework.data.jpa.repository.Query(
            "select m from LessonStyleMap m join fetch m.lessonStyleTag where m.lesson.lessonId in :lessonIds")
    List<LessonStyleMap> findAllWithTagByLessonIds(
            @org.springframework.data.repository.query.Param("lessonIds") java.util.Collection<Long> lessonIds);

}
