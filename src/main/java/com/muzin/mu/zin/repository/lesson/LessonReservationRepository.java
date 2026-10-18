package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.entity.lesson.TimeSlotStatus;
import com.muzin.mu.zin.entity.reservation.LessonReservation;
import com.muzin.mu.zin.entity.reservation.ReservationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LessonReservationRepository extends JpaRepository<LessonReservation, Long> {

    // 해당 유저가 예약한 레슨을 찾음
    @EntityGraph(attributePaths = {"timeSlot", "lesson"})
    Optional<LessonReservation> findByReservationIdAndUser_UserId(Long reservationId, Long userId);

    // 레슨 요청 온 유저 목록을 최신 순 부터 정렬한 리스트
    @EntityGraph(attributePaths = {"timeSlot", "lesson"})
    List<LessonReservation> findAllByUser_UserIdOrderByRequestedDtDesc(Long userId);

    // 아티스트 소유권 검증(예약 레슨이 해당 아티스트- 내 소속인지) / 근데 지금 principalUser 쪽에 artistId 없어서 일단 User로 검증
    @EntityGraph(attributePaths = {"timeSlot", "lesson"})
    Optional<LessonReservation> findByReservationIdAndLesson_ArtistProfile_User_UserId(
            Long reservationId, Long userId
    );

    // 아티스트에게 요청온 레슨 목록
    @EntityGraph(attributePaths = {"timeSlot", "lesson", "user"})
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

    // 진행 중인 예약(요청 대기/확정/완료 대기) 개수 - 레슨 핵심 정보 수정·삭제 잠금 판단
    long countByLesson_LessonIdAndStatusIn(Long lessonId, java.util.Collection<ReservationStatus> statuses);

    // 해당 시간에 예약 이력(상태 무관)이 있는지 - 있으면 삭제 대신 닫기만 허용
    boolean existsByTimeSlot_TimeSlotId(Long timeSlotId);

    // 특정 레슨의 예약 목록
    @EntityGraph(attributePaths = {"timeSlot", "lesson"})
    List<LessonReservation> findByLesson_LessonIdOrderByRequestedDtDesc(Long lessonId);

    // timeSlot endDt 기준으로 COMPLETION_PENDING 을 잡아줌
    @Query("""
            select r
            from LessonReservation r
            join fetch r.timeSlot ts
            join fetch r.lesson l
            join fetch l.artistProfile ap
            join fetch ap.user au
            join fetch r.user u
            where r.status = com.muzin.mu.zin.entity.reservation.ReservationStatus.CONFIRMED
              and ts.endDt <= :now
            """)
    List<LessonReservation> findReservationsToMoveCompletionPending(@Param("now")LocalDateTime now);

    @Query("""
           select r
           from LessonReservation r
           join fetch r.timeSlot ts
           join fetch r.lesson l
           join fetch l.artistProfile ap
           join fetch ap.user au
           join fetch r.user u
           where r.status = com.muzin.mu.zin.entity.reservation.ReservationStatus.COMPLETION_PENDING
             and ts.endDt <= :baseTime
    """)
    List<LessonReservation> findReservationsToAutoComplete(@Param("baseTime") LocalDateTime baseTime);

    // 아티스트가 응답하지 않은 채 레슨 시작 시간이 지난 요청 (만료 대상)
    @Query("""
           select r
           from LessonReservation r
           join fetch r.timeSlot ts
           join fetch r.lesson l
           join fetch l.artistProfile ap
           join fetch ap.user au
           join fetch r.user u
           where r.status = com.muzin.mu.zin.entity.reservation.ReservationStatus.REQUESTED
             and ts.startDt <= :now
    """)
    List<LessonReservation> findRequestedReservationsToExpire(@Param("now") LocalDateTime now);



    // 채팅: 이 수강생과 아티스트 사이에 진행 중인 확정 예약이 있는지 (확정 · 완료 확인 중)
    @Query("""
            select count(r) > 0 from LessonReservation r
            where r.user.userId = :userId and r.lesson.artistProfile.artistProfileId = :artistProfileId
              and r.status in (com.muzin.mu.zin.entity.reservation.ReservationStatus.CONFIRMED,
                               com.muzin.mu.zin.entity.reservation.ReservationStatus.COMPLETION_PENDING)
            """)
    boolean existsActiveBetween(@Param("userId") Long userId, @Param("artistProfileId") Long artistProfileId);

    // 채팅: 마지막으로 완료된 레슨이 끝난 시각
    @Query("""
            select max(r.timeSlot.endDt) from LessonReservation r
            where r.user.userId = :userId and r.lesson.artistProfile.artistProfileId = :artistProfileId
              and r.status = com.muzin.mu.zin.entity.reservation.ReservationStatus.COMPLETED
            """)
    LocalDateTime findLastCompletedEnd(@Param("userId") Long userId, @Param("artistProfileId") Long artistProfileId);

    // 채팅방 맥락: 이 둘 사이 최근 예약
    @Query("""
            select r from LessonReservation r join fetch r.lesson join fetch r.timeSlot
            where r.user.userId = :userId and r.lesson.artistProfile.artistProfileId = :artistProfileId
            order by r.timeSlot.startDt desc
            """)
    List<LessonReservation> findRecentBetween(@Param("userId") Long userId, @Param("artistProfileId") Long artistProfileId,
                                             org.springframework.data.domain.Pageable pageable);

    // 리뷰를 쓸 수 있는 예약: 내 완료 예약 + 작성 기간 안 + 리뷰를 쓴 적 없음 (삭제한 리뷰 포함)
    @Query("""
            select r from LessonReservation r
            join fetch r.lesson l
            join fetch l.artistProfile ap
            join fetch ap.user
            join fetch r.timeSlot
            where r.user.userId = :userId
              and r.status = :status
              and r.completedDt >= :since
              and not exists (select 1 from Review v where v.reservation = r)
            order by r.completedDt desc
            """)
    List<LessonReservation> findReviewWritable(@Param("userId") Long userId,
                                               @Param("status") ReservationStatus status,
                                               @Param("since") LocalDateTime since);
}
