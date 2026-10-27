package com.muzin.mu.zin.service.lesson;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.TimeSlotResponse;
import com.muzin.mu.zin.dto.reservation.*;
import com.muzin.mu.zin.entity.ArtistStatus;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.lesson.LessonStatus;
import com.muzin.mu.zin.entity.lesson.LessonTimeSlot;
import com.muzin.mu.zin.entity.lesson.TimeSlotStatus;
import com.muzin.mu.zin.entity.notification.NotificationRefType;
import com.muzin.mu.zin.entity.notification.NotificationType;
import com.muzin.mu.zin.entity.reservation.CompletionSource;
import com.muzin.mu.zin.entity.reservation.CanceledBy;
import com.muzin.mu.zin.entity.reservation.LessonReservation;
import com.muzin.mu.zin.entity.reservation.ReservationStatus;
import com.muzin.mu.zin.event.NotificationEvent;
import com.muzin.mu.zin.exception.ApiException;
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
    private final com.muzin.mu.zin.service.chat.ChatService chatService;

    // 예약 생성
    @Transactional
    public ApiRespDto<ReservationResponse> createReservation(ReservationCreateRequest req, PrincipalUser principalUser) {
        Long userId = principalUser.getUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저가 존재하지 않습니다."));

        LessonTimeSlot timeSlot = timeSlotRepository.findByIdForUpdate(req.timeSlotId())
                .orElseThrow(() -> ApiException.notFound("예약 가능한 시간이 아닙니다. 다시 시도해주세요."));

        if (timeSlot.getStatus() != TimeSlotStatus.OPEN) {
            throw ApiException.conflict("SLOT_NOT_AVAILABLE", "예약 가능한 시간이 아닙니다. 다시 시도해주세요.");
        }

        // 이미 시작했거나 지난 시간은 예약 불가
        if (!timeSlot.getStartDt().isAfter(TimeDefaults.nowKst())) {
            throw ApiException.conflict("SLOT_NOT_AVAILABLE", "이미 지난 시간은 예약할 수 없습니다.");
        }

        // 공개 중인 레슨 + 승인된 아티스트의 레슨만 예약 가능
        Lesson targetLesson = timeSlot.getLesson();
        if (targetLesson.isDeleted() || targetLesson.getStatus() != LessonStatus.ACTIVE
                || targetLesson.getArtistProfile().getUser().getArtistStatus() != ArtistStatus.APPROVED) {
            throw ApiException.conflict("LESSON_NOT_AVAILABLE", "현재 예약할 수 없는 레슨입니다.");
        }

        // 본인 레슨은 예약 불가
        if (targetLesson.getArtistProfile().getUser().getUserId().equals(userId)) {
            throw ApiException.badRequest("본인 레슨은 예약할 수 없습니다.");
        }

        // 이메일 인증 완료 유저만 예약 가능 (소셜 가입은 제공자가 확인해준 경우 가입 시 인증 완료)
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "이메일 인증 후 예약할 수 있어요. 마이페이지 > 내 정보에서 인증 메일을 받아주세요.");
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

        Lesson lesson = targetLesson;
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
    public ApiRespDto<List<ArtistReservationDetailResponse>> getMyReservationList(PrincipalUser principalUser) {
        Long userId = principalUser.getUserId();

        List<LessonReservation> list = reservationRepository.findAllByUser_UserIdOrderByRequestedDtDesc(userId);
        return new ApiRespDto<>("success", "",list.stream().map(this::toArtistDetail).toList());
    }

    // 유저 예약한 레슨 단일 조회
    @Transactional(readOnly = true)
    public ApiRespDto<ArtistReservationDetailResponse> getMyReservation(Long reservationId, PrincipalUser principalUser) {
        Long userId = principalUser.getUserId();

        LessonReservation reservation = reservationRepository.findByReservationIdAndUser_UserId(reservationId, userId)
                .orElseThrow(() -> ApiException.notFound("예약을 찾을 수 없습니다."));

        return new ApiRespDto<>("success", "조회 완료", toArtistDetail(reservation));
    }

    // 유저 예약 취소
    @Transactional
    public ApiRespDto<?> cancelMyReservation(Long reservationId, PrincipalUser principalUser) {
        Long userId = principalUser.getUserId();

        LessonReservation reservation = reservationRepository.findByReservationIdAndUser_UserId(reservationId,userId)
                .orElseThrow(() -> ApiException.notFound("예약을 찾을 수 없습니다.")); // 내 예약 아님

        rejectIfFinished(reservation);

        LocalDateTime now = LocalDateTime.now(KST);
        LocalDateTime startDt = reservation.getTimeSlot().getStartDt();
        if (!startDt.isAfter(now)) {
            throw new IllegalStateException("이미 시작된 레슨은 취소할 수 없습니다.");
        }

        // 대기중(REQUESTED)은 언제든 취소 가능하게
        if (reservation.getStatus() == ReservationStatus.REQUESTED) {
            reservation.cancel(CanceledBy.USER);
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
                throw ApiException.conflict("CANCEL_DEADLINE_PASSED", "확정된 예약은 레슨 24시간 전까지만 취소할 수 있습니다. 아티스트에게 문의해주세요.");
            }

            reservation.cancel(CanceledBy.USER);

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

        throw new IllegalStateException("취소할 수 없는 상태입니다.");
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
                .orElseThrow(() -> ApiException.notFound("예약을 찾을 수 없습니다."));

        return new ApiRespDto<>("success", "조회 완료", toArtistDetail(reservation));
    }


    // 아티스트 예약 확정
    @Transactional
    public ApiRespDto<?> confirmReservation(Long reservationId, PrincipalUser principalUser) {
        Long artistUserId = principalUser.getUserId();

        LessonReservation reservation = getArtistReservationOrThrow(reservationId, artistUserId);

        if (reservation.getStatus() != ReservationStatus.REQUESTED) {
            throw new IllegalStateException("대기 중인 예약 요청만 확정할 수 있습니다.");
        }

        LessonTimeSlot slot = reservation.getTimeSlot();
        if (!slot.getStartDt().isAfter(TimeDefaults.nowKst())) {
            throw new IllegalStateException("이미 시작 시간이 지난 요청은 확정할 수 없습니다.");
        }
        if (slot.getStatus() != TimeSlotStatus.PENDING) {
            throw new IllegalStateException("슬롯 상태가 올바르지 않습니다. 새로고침 후 다시 시도해주세요.");
        }

        reservation.confirm();
        slot.book(); // 슬롯을 예약상태로 바꿔줌

        // 처음 수락이면 수강생-아티스트 채팅방 생성 (이미 있으면 그대로 -> 수락으로 다시 대화 가능해짐)
        chatService.ensureRoom(reservation.getUser(), reservation.getLesson().getArtistProfile());

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

        LessonReservation reservation = getArtistReservationOrThrow(reservationId, artistUserId);

        if (reservation.getStatus() != ReservationStatus.REQUESTED) {
            throw new IllegalStateException("대기 중인 예약 요청만 거절할 수 있습니다.");
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

        LessonReservation reservation = getArtistReservationOrThrow(reservationId, artistUserId);

        rejectIfFinished(reservation);

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("확정된 예약만 취소할 수 있습니다.");
        }

        // 수강생과 같은 기준: 레슨 24시간 전까지만 (예전엔 화면에서만 막고 서버는 언제든 허용)
        if (TimeDefaults.nowKst().isAfter(reservation.getTimeSlot().getStartDt().minusHours(24))) {
            throw ApiException.conflict("CANCEL_DEADLINE_PASSED", "레슨 24시간 전부터는 취소할 수 없어요. 수강생과 직접 일정을 조율해주세요.");
        }

        String reason = (req == null || req.reason() == null) ? "" : req.reason().trim();
        if (reason.length() > 200) {
            throw new IllegalArgumentException("취소 사유는 200자 이하로 입력해주세요.");
        }

        reservation.cancel(CanceledBy.ARTIST);

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
                // 아티스트가 적은 사유를 수강생 알림에 함께 (예전엔 받기만 하고 버려졌음)
                reason.isEmpty() ? "아티스트 사정으로 예약이 취소되었습니다." : "아티스트 사정으로 예약이 취소되었습니다. 사유: " + reason,
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
    @Transactional
    public void autoCompleteReservations() {
        LocalDateTime baseTime = TimeDefaults.nowKst().minusHours(1);

        List<LessonReservation> reservations =
                reservationRepository.findReservationsToAutoComplete(baseTime);

        for (LessonReservation reservation : reservations) {
            reservation.autoComplete();
            reservation.getTimeSlot().closeFromBooked();

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
        LessonReservation reservation = getArtistReservationOrThrow(reservationId, artistUserId);

        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 레슨입니다.");
        }

        if (reservation.getStatus() != ReservationStatus.COMPLETION_PENDING) {
            throw new IllegalStateException("완료 처리 가능한 상태가 아닙니다.");
        }

        reservation.completeByArtist();
        reservation.getTimeSlot().closeFromBooked();

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


    // 응답 없이 시작 시간이 지난 예약 요청 만료 (스케줄러)
    // 예약 -> EXPIRED, 슬롯 PENDING -> CLOSED (이미 지난 시간이라 다시 열지 않음), 요청한 유저에게 알림
    @Transactional
    public void expireUnansweredRequests() {
        List<LessonReservation> reservations =
                reservationRepository.findRequestedReservationsToExpire(TimeDefaults.nowKst());

        for (LessonReservation reservation : reservations) {
            reservation.expire();

            LessonTimeSlot slot = reservation.getTimeSlot();
            if (slot.getStatus() == TimeSlotStatus.PENDING) {
                slot.close();
            }

            publisher.publishEvent(new NotificationEvent(
                    reservation.getUser().getUserId(),
                    NotificationType.RESERVATION_EXPIRED,
                    "예약 요청 만료",
                    "아티스트가 응답하지 않아 예약 요청이 만료되었습니다.",
                    NotificationRefType.RESERVATION,
                    reservation.getReservationId()
            ));
        }
    }

    // 이미 끝난 예약(취소/거절/만료/완료)인지 확인
    private void rejectIfFinished(LessonReservation reservation) {
        switch (reservation.getStatus()) {
            case CANCELED -> throw new IllegalStateException("이미 취소된 예약입니다.");
            case REJECTED -> throw new IllegalStateException("이미 거절된 예약입니다.");
            case EXPIRED -> throw new IllegalStateException("만료된 예약입니다.");
            case COMPLETED, COMPLETION_PENDING -> throw new IllegalStateException("진행이 완료된 레슨은 취소할 수 없습니다.");
            default -> { }
        }
    }

    private LessonReservation getArtistReservationOrThrow(Long reservationId, Long artistUserId) {
        return reservationRepository
                .findByReservationIdAndLesson_ArtistProfile_User_UserId(reservationId, artistUserId)
                .orElseThrow(() -> ApiException.notFound("예약을 찾을 수 없습니다."));
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
                r.getCompletionPendingDt(),
                r.getCompletedDt(),
                r.getCompletionSource(),

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

        boolean myCompletionConfirmed =
                r.getCompletionSource() == CompletionSource.ARTIST;

        boolean canMarkCompleted =
                r.getStatus() == ReservationStatus.COMPLETION_PENDING
                && !myCompletionConfirmed;

        return new ArtistReservationDetailResponse(
                r.getReservationId(),
                r.getStatus(),
                r.getPriceAtBooking(),

                r.getRequestedDt(),
                r.getConfirmedDt(),
                r.getCanceledDt(),
                r.getCompletionPendingDt(),
                r.getCompletedDt(),
                r.getCompletionSource(),

                lesson.getLessonId(),
                lesson.getTitle(),
                new TimeSlotResponse(ts.getTimeSlotId(), ts.getStartDt(), endDt, ts.getStatus()),

                artistUser.getUserId(),
                artistUser.getUsername(),

                requesterUser.getUserId(),
                requesterUser.getUsername(),
                r.getRequestedMsg(),

                canMarkCompleted,
                myCompletionConfirmed
        );
    }
}
