package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.entity.lesson.LessonStyleTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LessonStyleTagRepository extends JpaRepository<LessonStyleTag, Long> {

    // 레슨 스타일 리스트 조회
    List<LessonStyleTag> findAllByOrderByStyleNameAsc();
}
