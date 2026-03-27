package com.muzin.mu.zin.service.lesson;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.TimeSlotResponse;
import com.muzin.mu.zin.dto.reservation.*;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.lesson.LessonTimeSlot;
import com.muzin.mu.zin.entity.lesson.TimeSlotStatus;
import com.muzin.mu.zin.entity.notification.NotificationRefType;
import com.muzin.mu.zin.entity.notification.NotificationType;
import com.muzin.mu.zin.entity.reservation.LessonReservation;
import com.muzin.mu.zin.entity.reservation.ReservationStatus;
import com.muzin.mu.zin.event.NotificationEvent;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.repository.lesson.LessonReservationRepository;
import com.muzin.mu.zin.repository.lesson.LessonTimeSlotRepository;
import com.muzin.mu.zin.security.model.PrincipalUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LessonReservationService {

    private final LessonReservationRepository reservationRepository;
    private final LessonTimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final ApplicationEventPublisher publisher; // 이벤트 리스너 사용 시 필요

    // 예약 생성
    @Transactional
    public ApiRespDto<ReservationResponse> createReservation(ReservationCreateRequest req, PrincipalUser principalUser) {
        Long userId = principalUser.getUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저가 존재하지 않습니다."));

        LessonTimeSlot timeSlot = timeSlotRepository.findByIdForUpdate(req.timeSlotId())
                .orElseThrow(() -> new IllegalArgumentException("예약 가능한 시간이 아닙니다. 다시 시도해주세요."));

        if (timeSlot.getStatus() != TimeSlotStatus.OPEN) {
            return new ApiRespDto<>("failed", "예약 가능한 시간이 아닙니다. 다시 시도해주세요.", null);
        }

        // slot_id 가 UK 니까
//        if (reservationRepository.existsByTimeSlot_TimeSlotIdAndStatusIn(timeSlot.getTimeSlotId())) {
//            return new ApiRespDto<>("failed", "이미 예약 요청된 시간입니다.", null);
//        }
        // timeSlot 자체에 unique가 걸려있는데, 이렇게 되면 db자체에 행은 남아있고 unique가 걸려있는 상황이라 해당 시간에 재예약을 못함
        // 그래서 활성 상태만 체크해서 막아줘야함(요청 되거나 예약 완료된 것들)
//        List<ReservationStatus> active = List.of(ReservationStatus.REQUESTED, ReservationStatus.CONFIRMED);
//        if (reservationRepository.existsByTimeSlot_TimeSlotIdAndStatusIn(req.timeSlotId(), active)) {
//            return new ApiRespDto<>("failed", "이미 예약 요청된 시간입니다.", null);
//        }

        // 중복 예약 방지 - 예약 요청 들어오는 순간 해당 타임 슬롯을 Booked로 바꿔줌 - 도메인 메서드 만든거 사용
//        timeSlot.book();
        // 요청하는 순간 booked -> pending
        timeSlot.pending();

        Lesson lesson = timeSlot.getLesson();
        Integer price = (lesson.getPrice() == null ? 0 : lesson.getPrice()); // 예약 당시 가격

        LessonReservation reservation = LessonReservation.builder()
                .user(user)
                .lesson(lesson)
                .timeSlot(timeSlot)
                .status(ReservationStatus.REQUESTED)
                .priceAtBooking(price)
                .requestedMsg(req.requestMsg())
                .requestedDt(LocalDateTime.now(KST))
                .build();

        LessonReservation saved = reservationRepository.save(reservation);

        // artist userId
        Long artistUserId = saved.getLesson().getArtistProfile().getUser().getUserId();

        // 유저의 예약 요청 생성 -> 아티스트에게 알림
        // 나중에 해당 로직들은 팩토리로 빼보기
        publisher.publishEvent(new NotificationEvent(
                artistUserId,
                NotificationType.RESERVATION_REQUESTED,
                "새 예약 요청",
                "예약 요청이 도착했습니다.",
                NotificationRefType.RESERVATION,
                saved.getReservationId()
        ));

        return new ApiRespDto<>("success", "예약 요청 완료", toResponse(saved));
    }

    // 유저 예약한 레슨 목록 리스트
    @Transactional(readOnly = true)
    public ApiRespDto<List<ReservationResponse>> getMyReservationList(PrincipalUser principalUser) {
        Long userId = principalUser.getUserId();

        List<LessonReservation> list = reservationRepository.findAllByUser_UserIdOrderByRequestedDtDesc(userId);
        return new ApiRespDto<>("success", "",list.stream().map(this::toResponse).toList());
    }

    // 유저 예약한 레슨 단일 조회
    @Transactional(readOnly = true)
    public ApiRespDto<ReservationResponse> getMyReservation(Long reservationId, PrincipalUser principalUser) {
        Long userId = principalUser.getUserId();

        LessonReservation reservation = reservationRepository.findByReservationIdAndUser_UserId(reservationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("잘못된 접근입니다. 다시 시도해주세요."));

        return new ApiRespDto<>("success", "조회 완료", toResponse(reservation));
    }

    // 유저 예약 취소
    @Transactional
    public ApiRespDto<?> cancelMyReservation(Long reservationId, PrincipalUser principalUser) {
        Long userId = principalUser.getUserId();

        LessonReservation reservation = reservationRepository.findByReservationIdAndUser_UserId(reservationId,userId)
                .orElseThrow(() -> new IllegalArgumentException("잘못된 접근입니다. 다시 시도해주세요.")); // 내 예약 아님

        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            return new ApiRespDto<>("failed", "이미 취소된 예약입니다.", null);
        }

        if (reservation.getStatus() == ReservationStatus.REJECTED) {
            return new ApiRespDto<>("failed", "이미 거절된 예약입니다.", null);
        }

        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            return new ApiRespDto<>("failed","진행이 완료된 레슨은 취소할 수 없습니다.",null);
        }

        LocalDateTime now = LocalDateTime.now(KST);
        LocalDateTime startDt = reservation.getTimeSlot().getStartDt();
        if (!startDt.isAfter(now)) {
            return new ApiRespDto<>("failed", "이미 진행중인 레슨은 취소할 수 없습니다.", null);
        }

        // 대기중(REQUESTED)은 언제든 취소 가능하게
        if (reservation.getStatus() == ReservationStatus.REQUESTED) {
            reservation.cancel();
            if (reservation.getTimeSlot().getStatus() == TimeSlotStatus.PENDING) {
                reservation.getTimeSlot().open(); // PENDING -> OPEN
            }

            // 대기중일 때 취소
            Long artistUserId = reservation.getLesson().getArtistProfile().getUser().getUserId();
            publisher.publishEvent(new NotificationEvent(
                    artistUserId,
                    NotificationType.RESERVATION_CANCELED_BY_USER,
                    "예약 취소",
                    "유저가 예약을 취소했습니다.",
                    NotificationRefType.RESERVATION,
                    reservation.getReservationId()
            ));

            return new ApiRespDto<>("success", "예약 요청이 취소되었습니다.", null);
        }

        // 확정(CONFIRMED)은 24시간 룰 적용
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            if (now.isAfter(startDt.minusHours(24))) {
                return new ApiRespDto<>("failed", "취소가 불가능한 상태입니다. 아티스트에게 채팅으로 문의하세요.", null);
            }

            reservation.cancel();

            // 유저 취소 시에는 다시 OPEN으로
            reservation.getTimeSlot().reopenFromBooked(); // BOOKED -> OPEN

            // 예약 확정 된 상태에서 취소
            Long artistUserId = reservation.getLesson().getArtistProfile().getUser().getUserId();
            publisher.publishEvent(new NotificationEvent(
                    artistUserId,
                    NotificationType.RESERVATION_CANCELED_BY_USER,
                    "예약 취소",
                    "유저가 예약을 취소했습니다.",
                    NotificationRefType.RESERVATION,
                    reservation.getReservationId()
            ));

            return new ApiRespDto<>("success", "예약 취소가 완료되었습니다.", null);
        }

        return new ApiRespDto<>("failed", "취소할 수 없는 상태입니다.", null);
    }

    // 아티스트 예약 조회 리스트
    @Transactional(readOnly = true)
    public ApiRespDto<List<ArtistReservationSummaryResponse>> getArtistReservationList(ReservationStatus status, PrincipalUser principalUser) {
        Long artistUserId = principalUser.getUserId();
        List<LessonReservation> list = reservationRepository.findArtistReservations(artistUserId, status);

        return new ApiRespDto<>("success", "예약 리스트 조회 완료", list.stream().map(this::toArtistSummary).toList());
    }

    // 아티스트 예약 단일 조회
    @Transactional(readOnly = true)
    public ApiRespDto<ArtistReservationDetailResponse> getArtistReservation(Long reservationId, PrincipalUser principalUser) {
        Long artistUserId = principalUser.getUserId();

        LessonReservation reservation = reservationRepository
                .findByReservationIdAndLesson_ArtistProfile_User_UserId(reservationId, artistUserId)
                .orElseThrow(() -> new IllegalArgumentException("잘못된 접근입니다. 다시 시도해주세요."));

        return new ApiRespDto<>("success", "조회 완료", toArtistDetail(reservation));
    }


    // 아티스트 예약 확정
    @Transactional
    public ApiRespDto<?> confirmReservation(Long reservationId, PrincipalUser principalUser) {
        Long artistUserId = principalUser.getUserId();

        LessonReservation reservation =
                reservationRepository.findByReservationIdAndLesson_ArtistProfile_User_UserId(reservationId, artistUserId)
                        .orElseThrow(() -> new IllegalArgumentException("잘못된 접근입니다. 다시 시도해주세요."));

        if (reservation.getStatus() != ReservationStatus.REQUESTED) {
            return new ApiRespDto<>("failed","요청된 예약만 확인 가능합니다.", null);
        }

        LessonTimeSlot slot = reservation.getTimeSlot();
        if (slot.getStatus() != TimeSlotStatus.PENDING) {
            return new ApiRespDto<>("failed", "슬롯 상태가 올바르지 않습니다.(PENDING이 아님)", null);
        }

        reservation.confirm();
        slot.book(); // 슬롯을 예약상태로 바꿔줌

        // 알림 보낼 요청 온 유저
        Long userId = reservation.getUser().getUserId();
        publisher.publishEvent(new NotificationEvent(
                userId,
                NotificationType.RESERVATION_CONFIRMED,
                "예약 확정",
                "예약이 확정되었습니다.",
                NotificationRefType.RESERVATION,
                reservation.getReservationId()
        ));

        // 타임 슬롯은 이미 BOOKED 상태 유지
        return new ApiRespDto<>("success","예약 확정이 완료되었습니다.",null);
    }

    @Transactional
    public ApiRespDto<?> rejectReservation(Long reservationId, PrincipalUser principalUser) {
        Long artistUserId = principalUser.getUserId();

        LessonReservation reservation =
                reservationRepository.findByReservationIdAndLesson_ArtistProfile_User_UserId(reservationId, artistUserId)
                        .orElseThrow(() -> new IllegalArgumentException("잘못된 접근입니다. 다시 시도해주세요."));

        if (reservation.getStatus() != ReservationStatus.REQUESTED) {
            return new ApiRespDto<>("failed","요청된 예약만 확인 가능합니다.", null);
        }

        reservation.reject();
//        reservation.getTimeSlot().open();

        // 요청 거절 시 PENDING -> OPEN
        if (reservation.getTimeSlot().getStatus() == TimeSlotStatus.PENDING) {
            reservation.getTimeSlot().open();
        }

        Long userId = reservation.getUser().getUserId();
        publisher.publishEvent(new NotificationEvent(
                userId,
                NotificationType.RESERVATION_REJECTED,
                "예약 거절",
                "예약 요청이 거절되었습니다.",
                NotificationRefType.RESERVATION,
                reservation.getReservationId()
        ));

        return new ApiRespDto<>("success","예약 거절 완료",null);
    }

    // 아티스트 예약 취소
    @Transactional
    public ApiRespDto<?> cancelByArtist(Long reservationId, ArtistCancelRequest req, PrincipalUser principalUser) {
        Long artistUserId = principalUser.getUserId();

        LessonReservation reservation = reservationRepository
                .findByReservationIdAndLesson_ArtistProfile_User_UserId(reservationId, artistUserId)
                .orElseThrow(() -> new IllegalArgumentException("잘못된 접근입니다. 다시 시도해주세요."));

        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            return new ApiRespDto<>("failed","이미 취소된 예약입니다.", null);
        }

        if (reservation.getStatus() == ReservationStatus.REJECTED) {
            return new ApiRespDto<>("failed","이미 거절된 예약입니다.",null);
        }

        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            return new ApiRespDto<>("failed","진행이 완료된 레슨은 취소할 수 없습니다.", null);
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            return new ApiRespDto<>("failed", "확정된 예약만 취소할 수 있습니다.", null);
        }

        reservation.cancel();

        // 확정 취소 - 기본은 닫기, 옵션으로 재오픈
        if (req != null && req.reopenSlot()) {
            reservation.getTimeSlot().reopenFromBooked();   // BOOKED -> OPEN
        } else {
            reservation.getTimeSlot().closeFromBooked();    // BOOKED -> CLOSED
        }

        Long userId = reservation.getUser().getUserId();
        publisher.publishEvent(new NotificationEvent(
                userId,
                NotificationType.RESERVATION_CANCELED_BY_ARTIST,
                "예약 취소",
                "아티스트 사정으로 예약이 취소되었습니다.",
                NotificationRefType.RESERVATION,
                reservation.getReservationId()
        ));

        return new ApiRespDto<>("success", "아티스트 사정으로 예약이 취소되었습니다.", null);
    }

    // 완료 레슨 대기 전환
    @Transactional
    public void moveReservationToCompletionPending() {
        LocalDateTime now = TimeDefaults.nowKst();

        List<LessonReservation> reservations =
                reservationRepository.findReservationsToMoveCompletionPending(now);

        for (LessonReservation reservation : reservations) {
            reservation.markCompletionPending();

            Long artistUserId = reservation.getLesson()
                    .getArtistProfile()
                    .getUser()
                    .getUserId();

            publisher.publishEvent(new NotificationEvent(
                    artistUserId,
                    NotificationType.RESERVATION_COMPLETION_PENDING,
                    "레슨 완료 요청",
                    "레슨 시간이 만료된 레슨이 있습니다. 완료 처리를 해주세요.",
                    NotificationRefType.RESERVATION,
                    reservation.getReservationId()
            ));
        }
    }

    // 레슨 자동 완료
    public void autoCompleteReservations() {
        LocalDateTime baseTime = TimeDefaults.nowKst().minusHours(1);

        List<LessonReservation> reservations =
                reservationRepository.findReservationsToAutoComplete(baseTime);

        for (LessonReservation reservation : reservations) {
            reservation.autoComplete();

            Long userId = reservation.getUser().getUserId();

            publisher.publishEvent(new NotificationEvent(
                    userId,
                    NotificationType.RESERVATION_COMPLETED,
                    "레슨 완료",
                    "레슨이 자동으로 완료 처리되었습니다.",
                    NotificationRefType.RESERVATION,
                    reservation.getReservationId()
            ));
        }
    }

    // 아티스트 수동 완료
    @Transactional
    public ApiRespDto<?> completeReservationByArtist(Long reservationId, Long artistUserId) {
        LessonReservation reservation = reservationRepository
                .findByReservationIdAndLesson_ArtistProfile_User_UserId(reservationId, artistUserId)
                .orElseThrow(() -> new IllegalArgumentException("잘못된 접근입니다. 다시 시도해주세요."));

        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            return new ApiRespDto<>("failed","이미 완료된 레슨입니다.",null);
        }

        if (reservation.getStatus() != ReservationStatus.COMPLETION_PENDING) {
            return new ApiRespDto<>("failed", "완료 처리 가능한 상태가 아닙니다.",null);
        }

        reservation.completeByArtist();

        Long userId = reservation.getUser().getUserId();

        publisher.publishEvent(new NotificationEvent(
                userId,
                NotificationType.RESERVATION_COMPLETED,
                "레슨 완료",
                "레슨이 완료 처리되었습니다.",
                NotificationRefType.RESERVATION,
                reservation.getReservationId()
        ));

        return new ApiRespDto<>("success", "레슨이 완료 처리되었습니다.", null);


    }


    // 공통 유틸 toResponse
    // DTO 변환 과정에서 LAZY 연관을 매번 꺼내 쓰는 구조라서 N+1이 생김 (
    // Repository쪽 EntityGraph 붙이는걸로 해결
    private ReservationResponse toResponse(LessonReservation reservation) {
        LessonTimeSlot timeSlot = reservation.getTimeSlot();
        Lesson lesson = reservation.getLesson();

        // endDt는 응답에서 계산
        LocalDateTime endDt = timeSlot.getStartDt().plusMinutes(lesson.getDurationMin());

        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getStatus(),
                reservation.getPriceAtBooking(),
                reservation.getRequestedDt(),
                reservation.getConfirmedDt(),
                reservation.getCanceledDt(),
                lesson.getLessonId(),
                lesson.getTitle(),
                new TimeSlotResponse(
                        timeSlot.getTimeSlotId(),
                        timeSlot.getStartDt(),
                        endDt,
                        timeSlot.getStatus() // enum 그대로 내려도 JSON 에서 OPEN 처럼 직렬화됨
                )
        );
    }

    // 아티스트 관리화면 list 전용 response
    private ArtistReservationSummaryResponse toArtistSummary(LessonReservation r) {
        LessonTimeSlot ts = r.getTimeSlot();
        Lesson lesson = r.getLesson();
        LocalDateTime endDt = ts.getStartDt().plusMinutes(lesson.getDurationMin());

        String msg = r.getRequestedMsg();
        boolean hasMessage = (msg != null && !msg.isBlank());

        return new ArtistReservationSummaryResponse(
                r.getReservationId(),
                r.getStatus(),
                r.getPriceAtBooking(),
                r.getRequestedDt(),
                r.getConfirmedDt(),
                r.getCanceledDt(),
                lesson.getLessonId(),
                lesson.getTitle(),
                new TimeSlotResponse(ts.getTimeSlotId(), ts.getStartDt(), endDt, ts.getStatus()),
                r.getUser().getUserId(),
                r.getUser().getUsername(),
                hasMessage
        );
    }

    private ArtistReservationDetailResponse toArtistDetail(LessonReservation r) {
        LessonTimeSlot ts = r.getTimeSlot();
        Lesson lesson = r.getLesson();
        LocalDateTime endDt = ts.getStartDt().plusMinutes(lesson.getDurationMin());

        User requesterUser = r.getUser();
        User artistUser = lesson.getArtistProfile().getUser();

        return new ArtistReservationDetailResponse(
                r.getReservationId(),
                r.getStatus(),
                r.getPriceAtBooking(),
                r.getRequestedDt(),
                r.getConfirmedDt(),
                r.getCanceledDt(),
                lesson.getLessonId(),
                lesson.getTitle(),
                new TimeSlotResponse(ts.getTimeSlotId(), ts.getStartDt(), endDt, ts.getStatus()),

                artistUser.getUserId(),
                artistUser.getUsername(),

                requesterUser.getUserId(),
                requesterUser.getUsername(),

                r.getRequestedMsg()
        );
    }
}
