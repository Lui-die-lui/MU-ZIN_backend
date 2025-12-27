package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.entity.lesson.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    // 본인 레슨 소유권 체크용
    Optional<Lesson> findByLessonIdAndArtistProfile_ArtistProfileId(Long lessonId, Long artistProfileId);
}
