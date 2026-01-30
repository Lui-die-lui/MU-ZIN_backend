package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.entity.instrument.InstrumentCategory;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.lesson.LessonMode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findAllByArtistProfile_ArtistProfileIdOrderByLessonIdDesc(Long artistProfileId);

    // 본인 레슨 소유권 체크용
    Optional<Lesson> findByLessonIdAndArtistProfile_ArtistProfileId(Long lessonId, Long artistProfileId);

    // 삭제 안 된 것만 목록 조회
    List<Lesson> findAllByArtistProfile_ArtistProfileIdAndDeletedDtIsNullOrderByLessonIdDesc(Long artistProfileId);

    // 검색 -
    // 스타일 태그는 매핑 테이블 있음 - join 필요
    // 검색란이 빈 채로 검색을 하면 (빈 문자열이면) 그냥 전체조회 - 나중에 유저위치 기반 시에 있는 레슨 받아올 예정
    @Query("""
    select l
    from Lesson l
    where l.deletedDt is null
      and l.status = com.muzin.mu.zin.entity.lesson.LessonStatus.ACTIVE
      and (:mode is null or l.mode = :mode)
      and (
            :keyword is null or :keyword = '' or
            lower(l.title) like lower(concat('%', :keyword, '%')) or
            lower(coalesce(l.description, '')) like lower(concat('%', :keyword, '%'))
      )
      and (
            :styleTagIds is null or exists (
                select 1
                from ArtistStyleMap asm
                where asm.artistProfile = l.artistProfile
                  and asm.lessonStyleTag.lessonStyleTagId in :styleTagIds
            )
      )
      and (:instIds is null or l.instrument.instId in :instIds)
      and (:instCategory is null or l.instrument.category = :instCategory)
      
     and exists (
              select 1
              from LessonTimeSlot ts
              where ts.lesson = l
                and ts.status = com.muzin.mu.zin.entity.lesson.TimeSlotStatus.OPEN
                and ts.startDt between :fromDt and :toDt
        )
""")
    List<Lesson> searchPublicLessons(
            @Param("keyword") String keyword,
            @Param("mode")LessonMode mode,
            @Param("styleTagIds") List<Long> styleTagIds,
            @Param("instCategory") InstrumentCategory instCategory,
            @Param("instIds") List<Long> instIds,
            @Param("fromDt") LocalDateTime fromDt,
            @Param("toDt") LocalDateTime toDt,
            Pageable pageable
            );

    @Query("""
            select l from Lesson l
            join fetch l.artistProfile ap
            join fetch ap.user u
            where l.lessonId = :lessonId
            """)
    Optional<Lesson> findPublicDetailById(Long lessonId);


}

