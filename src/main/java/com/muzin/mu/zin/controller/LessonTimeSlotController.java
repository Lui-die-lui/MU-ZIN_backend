package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.TimeSlotCreateRequest;
import com.muzin.mu.zin.dto.lesson.TimeSlotResponse;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.lesson.LessonTimeSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LessonTimeSlotController {

    private final LessonTimeSlotService lessonTimeSlotService;

    // 유저용 OPEN 슬롯 조회
    @GetMapping("/{lessonId}/time-slots")
    public ApiRespDto<List<TimeSlotResponse>> getOpenSlots(
            @PathVariable Long lessonId,
            @RequestParam LocalDateTime from,
            @RequestParam LocalDateTime to
            ) {
        return lessonTimeSlotService.getOpenSlot(lessonId, from, to);
    }

    // 아티스트용 레슨시간 슬롯 전체 조회
    @GetMapping("/me/{lessonId}/time-slots")
    public ApiRespDto<List<TimeSlotResponse>> getArtistSlots(
            @PathVariable Long lessonId,
            @RequestParam LocalDateTime from,
            @RequestParam LocalDateTime to,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        return lessonTimeSlotService.getArtistSlots(lessonId, from, to, principalUser);
    }

    // 아티스트용 슬롯 생성
    @PostMapping("/me/{lessonId}/time-slots")
    public ApiRespDto<?> createSlots(
            @PathVariable Long lessonId,
            @RequestBody TimeSlotCreateRequest req,
            @AuthenticationPrincipal PrincipalUser principalUser
            ) {
        return lessonTimeSlotService.createSlots(lessonId, req, principalUser);
    }

    // 아티스트용 슬롯 삭제 (slotId 기반 - lessonId면 lesson 이 지워짐...)
    @DeleteMapping("/me/time-slots/{timeSlotId}")
    public ApiRespDto<?> deleteSlot(
            @PathVariable Long timeSlotId,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        return lessonTimeSlotService.deleteSlot(timeSlotId, principalUser);
    }

    // 레슨 타임 슬롯 open / close
    @PatchMapping("/me/time-slots/{timeSlotId}/close")
    public ApiRespDto<?> closeSlot(@PathVariable Long timeSlotId, @AuthenticationPrincipal PrincipalUser principalUser) {
        return lessonTimeSlotService.closeSlot(timeSlotId, principalUser);
    }

    @PatchMapping("/me/time-slots/{timeSlotId}/open")
    public ApiRespDto<?> openSlot(@PathVariable Long timeSlotId, @AuthenticationPrincipal PrincipalUser principalUser) {
        return lessonTimeSlotService.openSlot(timeSlotId, principalUser);
    }

}
