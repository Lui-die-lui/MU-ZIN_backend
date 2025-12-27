package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.entity.lesson.LessonStyleMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LessonStyleMapRepository extends JpaRepository<LessonStyleMap, Long> {

    // 아티스트가 지정해놓은 레슨 스타일 제거
    void deleteByLesson_LessonId(Long lessonId);
    List<LessonStyleMap> findAllByLesson_LessonId(Long lessonId);
}
