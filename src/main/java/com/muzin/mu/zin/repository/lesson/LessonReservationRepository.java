package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.entity.reservation.LessonReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonReservationRepository extends JpaRepository<LessonReservation, Long> {

    boolean existsBySlot_TimeSlotId(Long timeSlotId);

    // 해당 유저가 예약한 레슨을 찾음
    Optional<LessonReservation> findByReservationIdAndUser_UserId(Long reservationId, Long userId);

    // 레슨 요청 온 유저 목록을 최신 순 부터 정렬한 리스트
    List<LessonReservation> findAllByUser_UserIdOrderByRequestedDtDesc(Long userId);

    // 아티스트 소유권 검증(예약 레슨이 해당 아티스트- 내 소속인지) / 근데 지금 principalUser 쪽에 artistId 없어서 일단 User로 검증
    Optional<LessonReservation> findByReservationAndLesson_ArtistProfile_User_UserId(
            Long reservationId, Long userId
    );

    // 특정 레슨의 예약 목록
    List<LessonReservation> findByLesson_LessonIdOrderByRequestedDtDesc(Long lessonId);



}
