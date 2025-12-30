package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.entity.reservation.LessonReservation;
import com.muzin.mu.zin.entity.reservation.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LessonReservationRepository extends JpaRepository<LessonReservation, Long> {

//    boolean existsByTimeSlot_TimeSlotId(Long timeSlotId);
    boolean existsByTimeSlot_TimeSlotIdAndStatusIn(Long timeSlotId, List<ReservationStatus> statuses);

    // 해당 유저가 예약한 레슨을 찾음
    Optional<LessonReservation> findByReservationIdAndUser_UserId(Long reservationId, Long userId);

    // 레슨 요청 온 유저 목록을 최신 순 부터 정렬한 리스트
    List<LessonReservation> findAllByUser_UserIdOrderByRequestedDtDesc(Long userId);

    // 아티스트 소유권 검증(예약 레슨이 해당 아티스트- 내 소속인지) / 근데 지금 principalUser 쪽에 artistId 없어서 일단 User로 검증
    Optional<LessonReservation> findByReservationIdAndLesson_ArtistProfile_User_UserId(
            Long reservationId, Long userId
    );

    // 아티스트에게 요청온 레슨 목록
    @Query("""
            select r from LessonReservation r
            where r.lesson.artistProfile.user.userId = :artistUserId
                and (:status is null or r.status = :status)
            order by r.requestedDt desc
            """)
    List<LessonReservation> findArtistReservations(
            @Param("artistUserId") Long artistUserId,
            @Param("status")ReservationStatus status
            );

    // 특정 레슨의 예약 목록
    List<LessonReservation> findByLesson_LessonIdOrderByRequestedDtDesc(Long lessonId);



}
