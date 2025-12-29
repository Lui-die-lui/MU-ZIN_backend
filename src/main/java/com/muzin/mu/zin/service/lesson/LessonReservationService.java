package com.muzin.mu.zin.service.lesson;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.TimeSlotResponse;
import com.muzin.mu.zin.dto.reservation.ReservationCreateRequest;
import com.muzin.mu.zin.dto.reservation.ReservationResponse;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.lesson.LessonTimeSlot;
import com.muzin.mu.zin.entity.lesson.TimeSlotStatus;
import com.muzin.mu.zin.entity.reservation.LessonReservation;
import com.muzin.mu.zin.entity.reservation.ReservationStatus;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.repository.lesson.LessonReservationRepository;
import com.muzin.mu.zin.repository.lesson.LessonTimeSlotRepository;
import com.muzin.mu.zin.security.model.PrincipalUser;
import lombok.RequiredArgsConstructor;
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
        if (reservationRepository.existsBySlot_TimeSlotId(timeSlot.getTimeSlotId())) {
            return new ApiRespDto<>("failed", "이미 예약 요청된 시간입니다.", null);
        }

        // 중복 예약 방지 - 예약 요청 들어오는 순간 해당 타임 슬롯을 Booked로 바꿔줌 - 도메인 메서드 만든거 사용
        timeSlot.book();

        Lesson lesson = timeSlot.getLesson();
        Integer price = (lesson.getPrice() == null ? 0 : lesson.getPrice()); // 예약 당시 가격

        LessonReservation reservation = LessonReservation.builder()
                .user(user)
                .lesson(lesson)
                .timeSlot(timeSlot)
                .status(ReservationStatus.REQUESTED)
                .priceAtBooking(price)
                .requestedMsg(req.requestMsg())
                .requestedDt(LocalDateTime.now())
                .build();

        LessonReservation saved = reservationRepository.save(reservation);

        return new ApiRespDto<>("success", "예약 요청 완료", toResponse(saved));
    }

    // 유저 예약한 레슨 보기
    @Transactional(readOnly = true)
    public ApiRespDto<List<ReservationResponse>> getMReservation(PrincipalUser principalUser) {
        Long userId = principalUser.getUserId();

        List<LessonReservation> list = reservationRepository.findAllByUser_UserIdOrderByRequestedDtDesc(userId);
        return new ApiRespDto<>("success", "",list.stream().map(this::toResponse).toList());
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

        // 24시간 이내면 자동취소 불가 -> 채팅으로 협의
        if (now.isAfter(startDt.minusHours(24))) {
            return new ApiRespDto<>("failed", "취소가 불가능한 상태입니다. 아티스트에게 채팅으로 문의하세요.",null);
        }
        // 예약이 취소 되면
        reservation.cancel();
        // 슬롯 다시 열어줌
        reservation.getTimeSlot().open();

        return new ApiRespDto<>("success","예약 취소가 완료되었습니다.", null);
    }

    // 아티스트 예약 확정
    @Transactional
    public ApiRespDto<?> confirmReservation(Long reservationId, PrincipalUser principalUser) {
        Long artistProfileId = principalUser.getUserId();

        LessonReservation reservation =
                reservationRepository.findByReservationAndLesson_ArtistProfile_User_UserId(reservationId, artistProfileId)
                        .orElseThrow(() -> new IllegalArgumentException("잘못된 접근입니다. 다시 시도해주세요."));

        if (reservation.getStatus() != ReservationStatus.REQUESTED) {
            return new ApiRespDto<>("failed","요청된 예약만 확인 가능합니다.", null);
        }

        reservation.confirm();

        // 타임 슬롯은 이미 BOOKED 상태 유지
        return new ApiRespDto<>("success","예약 확정 완료",null);
    }

    @Transactional
    public ApiRespDto<?> rejectReservation(Long reservationId, PrincipalUser principalUser) {
        Long artistProfileId = principalUser.getUserId();

        LessonReservation reservation =
                reservationRepository.findByReservationAndLesson_ArtistProfile_User_UserId(reservationId, artistProfileId)
                        .orElseThrow(() -> new IllegalArgumentException("잘못된 접근입니다. 다시 시도해주세요."));

        if (reservation.getStatus() != ReservationStatus.REQUESTED) {
            return new ApiRespDto<>("failed","요청된 예약만 확인 가능합니다.", null);
        }

        reservation.reject();
        reservation.getTimeSlot().open();

        return new ApiRespDto<>("success","예약 거절이 완료",null);
    }

    // 지금 일반 사용자 예약 취소는 있는데 아티스트 예약 취소는 없음


    // 공통 유틸 toResponse
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
                new TimeSlotResponse(
                        timeSlot.getTimeSlotId(),
                        timeSlot.getStartDt(),
                        endDt,
                        timeSlot.getStatus() // .name() 할 필요 없음 - 이미 string임
                )
        );
    }
}
