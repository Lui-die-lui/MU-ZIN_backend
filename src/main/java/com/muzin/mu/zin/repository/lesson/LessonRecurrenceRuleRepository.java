package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.entity.lesson.LessonRecurrenceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonRecurrenceRuleRepository extends JpaRepository<LessonRecurrenceRule, Long> {
    Optional<LessonRecurrenceRule> findByLesson_LessonId(Long lessonId);
    List<LessonRecurrenceRule> findAllByEnabledTrue();
}
