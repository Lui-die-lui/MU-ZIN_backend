package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.entity.lesson.LessonTimeSlot;
import com.muzin.mu.zin.entity.lesson.TimeSlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LessonTimeSlotRepository extends JpaRepository<LessonTimeSlot, Long> {

    // 해당 레슨에 시작시간 슬롯이 이미 있는지 조회 - 슬롯 생성 중복 방지
    boolean existsByLesson_LessonIdAndStartDt(Long lessonId, LocalDateTime startDt);

    // 해당 레슨의 슬롯 중 기간안에 있는 슬롯 목록 - 유저가 선택할 시간표 뿌리기
    List<LessonTimeSlot> findAllByLesson_LessonIdAndStartDtBetweenOrderByStartDtAsc(
            Long lessonId, LocalDateTime from, LocalDateTime to
    );

    // 해당 타임 슬롯이 아티스트의 레슨에 속한 슬롯인지 조회
    Optional<LessonTimeSlot> findByTimeSlotIdAndLesson_ArtistProfile_ArtistProfileId(Long timeSlotId, Long artistProfileId);

    // 해당 슬롯의 현재 status가 맞는지 - 삭제 가능 여부 체크
    // 선언된 메서드들은 전부 파싱해서 구현 프록시를 만들어서 existBy라고 오타내면 오류뜸
    boolean existsByTimeSlotIdAndStatus(Long timeSlotId, TimeSlotStatus status);

}
