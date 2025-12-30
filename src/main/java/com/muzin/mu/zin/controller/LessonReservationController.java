package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.reservation.ArtistCancelRequest;
import com.muzin.mu.zin.dto.reservation.ReservationCreateRequest;
import com.muzin.mu.zin.dto.reservation.ReservationResponse;
import com.muzin.mu.zin.entity.reservation.ReservationStatus;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.lesson.LessonReservationService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class LessonReservationController {

    private final LessonReservationService reservationService;

    // 유저 본인 예약 생성
    @PostMapping
    public ApiRespDto<ReservationResponse> createReservation(
            @RequestBody ReservationCreateRequest req,
            @AuthenticationPrincipal PrincipalUser principalUser
            ) {
        return reservationService.createReservation(req, principalUser);
    }

    // 유저 본인 예약 목록 리스트
    @GetMapping("/me")
    public ApiRespDto<List<ReservationResponse>> getMyReservationList(
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        return reservationService.getMyReservationList(principalUser);
    }

    // 유저 본인 예약 단일 조회
    @GetMapping("/me/{reservationId}")
    public ApiRespDto<ReservationResponse> getMyReservation(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        return reservationService.getMyReservation(reservationId, principalUser);
    }

    // 유저 본인 예약 취소
    @PatchMapping("/me/{reservationId}/cancel")
    public ApiRespDto<?> cancelMyReservation(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        return reservationService.cancelMyReservation(reservationId, principalUser);
    }

    // 아티스트 예약 목록 리스트
    @GetMapping("/artist")
    public ApiRespDto<List<ReservationResponse>> getArtistReservationList(
            @RequestParam(required = false)ReservationStatus status,
            @AuthenticationPrincipal PrincipalUser principalUser
            ) {
        return reservationService.getArtistReservationList(status, principalUser);
    }


    // 아티스트 예약 단일 조회
    @GetMapping("/artist/{reservationId}")
    public ApiRespDto<ReservationResponse> getArtistReservation(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        return reservationService.getArtistReservation(reservationId, principalUser);
    }

    // 아티스트 예약 확정
    @PatchMapping("/artist/{reservationId}/confirm")
    public ApiRespDto<?> confirmReservation(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        return reservationService.confirmReservation(reservationId, principalUser);
    }

    // 아티스트 예약 거절
    @PatchMapping("/artist/{reservationId}/reject")
    public ApiRespDto<?> rejectReservation(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        return reservationService.rejectReservation(reservationId, principalUser);
    }

    // 아티스트 확정된 예약 취소
    @PatchMapping("/artist/{reservationId}/cancel-by-artist")
    public ApiRespDto<?> cancelByArtist(
            @PathVariable Long reservationId,
            @RequestBody(required = false)ArtistCancelRequest req, // 바디 없이도 호출 가능하게 하려면 필요
            @AuthenticationPrincipal PrincipalUser principalUser
            ) {
        return reservationService.cancelByArtist(reservationId, req, principalUser);
    }
}
