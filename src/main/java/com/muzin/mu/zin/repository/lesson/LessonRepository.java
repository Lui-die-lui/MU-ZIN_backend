package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.entity.lesson.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findAllByArtistProfile_ArtistProfileIdOrderByLessonIdDesc(Long artistProfileId);

    // 본인 레슨 소유권 체크용
    Optional<Lesson> findByLessonIdAndArtistProfile_ArtistProfileId(Long lessonId, Long artistProfileId);
}
