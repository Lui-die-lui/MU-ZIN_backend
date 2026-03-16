package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.dto.lesson.LessonCardRow;
import com.muzin.mu.zin.dto.lesson.LessonSearchResponse;
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
join l.instrument i
where l.deletedDt is null
  and l.status = com.muzin.mu.zin.entity.lesson.LessonStatus.ACTIVE
  and (:mode is null or l.mode = :mode)

  and (
        :applyKeyword = false
        or lower(l.title) like lower(concat('%', :keyword, '%'))
        or lower(coalesce(l.description, '')) like lower(concat('%', :keyword, '%'))
  )

    and (
            :applyStyleTags = false or exists (
              select 1
              from ArtistStyleMap asm
              where asm.artistProfile = l.artistProfile
                and asm.lessonStyleTag.lessonStyleTagId in :styleTagIds
            )
          )
     
    and (
          :applyInstIds = false
          or l.instrument.instId in :instIds
        )
     and (:instCategory is null or l.instrument.category = :instCategory)

  and (
        :applyTime = false
        or exists (
            select 1
            from LessonTimeSlot ts
            where ts.lesson = l
              and ts.status = com.muzin.mu.zin.entity.lesson.TimeSlotStatus.OPEN
              and ts.startDt between :fromDt and :toDt
              
            and (
            :applyWeekday = false
            or cast(function('date_part', 'isodow', ts.startDt) as integer) in :daysOfWeek
            )
            
            and (
            :applyTimeParts  = false
            or (
            case
             when function('date_part', 'hour', ts.startDt) between 6 and 11 then 'MORNING'
             when function('date_part', 'hour', ts.startDt) between 12 and 17 then 'AFTERNOON'
             when function('date_part', 'hour', ts.startDt) between 18 and 23 then 'EVENING'
             else 'DAWN'
         end
            ) in :timeParts
        )
    )
)
""")
    List<Lesson> searchPublicLessons(
            @Param("keyword") String keyword,
            @Param("applyKeyword") boolean applyKeyword,

            @Param("mode") LessonMode mode,

            @Param("styleTagIds") List<Long> styleTagIds,
            @Param("applyStyleTags") boolean applyStyleTags,

            @Param("instCategory") InstrumentCategory instCategory,

            @Param("instIds") List<Long> instIds,
            @Param("applyInstIds") boolean applyInstIds,

            @Param("fromDt") LocalDateTime fromDt,
            @Param("toDt") LocalDateTime toDt,
            @Param("applyTime") boolean applyTime,

            @Param("daysOfWeek") List<Integer> daysOfWeek,
            @Param("applyWeekday") boolean applyWeekday,

            @Param("timeParts") List<String> timeParts,
            @Param("applyTimeParts") boolean applyTimeParts,

            Pageable pageable
    );


    @Query("""
            select l from Lesson l
            join fetch l.artistProfile ap
            join fetch ap.user u
            where l.lessonId = :lessonId
            """)
    Optional<Lesson> findPublicDetailById(Long lessonId);

    // 레슨 카드 목록용
    @Query("""
    select new com.muzin.mu.zin.dto.lesson.LessonCardRow(
        l.lessonId,
        l.title,
        l.price,
        l.durationMin,
        l.mode,
        i.instId,
        i.instName
    )
    from Lesson l
    join l.instrument i
    where l.artistProfile.artistProfileId = :artistProfileId
      and l.deletedDt is null
      and l.status = com.muzin.mu.zin.entity.lesson.LessonStatus.ACTIVE
    order by l.lessonId desc
""")
    List<LessonCardRow> findArtistLessonCardRows(@Param("artistProfileId") Long artistProfileId);


    // 선택 레슨 상세용
    @Query("""
    select l
    from Lesson l
    join fetch l.artistProfile ap
    join fetch ap.user u
    join fetch l.instrument i
    where l.lessonId = :lessonId
      and ap.artistProfileId = :artistProfileId
      and l.deletedDt is null
      and l.status = com.muzin.mu.zin.entity.lesson.LessonStatus.ACTIVE
""")
    Optional<Lesson> findArtistLessonDetail(
            @Param("artistProfileId") Long artistProfileId,
            @Param("lessonId") Long lessonId
    );
}

