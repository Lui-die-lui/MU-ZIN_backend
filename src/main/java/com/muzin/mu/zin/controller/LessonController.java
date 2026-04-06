package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.*;
import com.muzin.mu.zin.entity.instrument.InstrumentCategory;
import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonSort;
import com.muzin.mu.zin.repository.lesson.LessonSearchCond;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.lesson.LessonService;
import com.muzin.mu.zin.service.lesson.LessonStyleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/lessons")
public class LessonController {

    private final LessonService lessonService;
    private final LessonStyleService lessonStyleService;

    // 아티스트 레슨 생성
    @PostMapping("/me")
    public ApiRespDto<?> createLesson(
            @Valid // dto 검증하려면 있어야함(@notnull)
            @RequestBody LessonCreateRequest req, @AuthenticationPrincipal PrincipalUser principalUser) {
        return lessonService.createLesson(req, principalUser);
    }

    // 아티스트 레슨 수정
    @PatchMapping("/me/{lessonId}")
    public ApiRespDto<?> updateLesson(
            @PathVariable Long lessonId,
            @RequestBody LessonUpdateRequest req,
            @AuthenticationPrincipal PrincipalUser principalUser
            ) {
        return lessonService.updateLesson(lessonId, req, principalUser);
    }

    // 아티스트 레슨 삭제
    @DeleteMapping("/me/{lessonId}")
    public ApiRespDto<?> deleteLesson(@PathVariable Long lessonId, @AuthenticationPrincipal PrincipalUser principalUser) {
        return lessonService.deleteLesson(lessonId, principalUser);
    }


    // 아티스트 레슨 리스트 조회
    @GetMapping("/me")
    public ApiRespDto<?> getArtistLesson(@AuthenticationPrincipal PrincipalUser principalUser) {
        return lessonService.getArtistLesson(principalUser);
    }

    // 아티스트 레슨 단일 조회
    @GetMapping("/me/{lessonId}")
    public ApiRespDto<?> getArtistLessonDetail(@PathVariable Long lessonId, @AuthenticationPrincipal PrincipalUser principalUser) {
        return lessonService.getArtistLessonDetail(lessonId, principalUser);
    }

    // 태그 목록 조회 (프론트 칩/필터용)
    @GetMapping("/style-tags")
    public ApiRespDto<?> getLessonStyleTags() {
        return lessonStyleService.getLessonStyleTags();
    }

    // 아티스트 레슨 수업방식 수정(전체 교체)
    @PutMapping("/me/{lessonId}/style-tag")
    public ApiRespDto<?> setLessonStyleTags(
            @PathVariable Long lessonId,
            @RequestBody SetLessonStylesRequest req,
            @AuthenticationPrincipal PrincipalUser principalUser
            ) {
        return lessonStyleService.setLessonStyles(lessonId, req, principalUser);
    }

    // 레슨 검색 (나중에 지역 기반 추가 예정)
    @GetMapping
    public ApiRespDto<?> searchLessons(
//            @RequestParam(required = false) String keyword,
//            @RequestParam(required = false) LessonMode mode,
//            @RequestParam(required = false) List<Long> styleTagIds,
//            @RequestParam(required = false) InstrumentCategory instrumentCategory,
//            @RequestParam(required = false) List<Long> instIds,
//            @RequestParam(defaultValue = "LATEST") LessonSort sort,
//
//            @RequestParam(required = false)
//            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
//            LocalDateTime from,
//
//            @RequestParam(required = false)
//            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
//            LocalDateTime to,
//
//            @RequestParam(required = false) List<Integer> daysOfWeek,
//            @RequestParam(required = false) List<String> timeParts
            @ModelAttribute LessonSearchRequest req
            ) {
        return lessonService.searchLessons(req);
    }

    // 레슨 단일 조회
    @GetMapping("/{lessonId}")
    public ApiRespDto<LessonDetailResponse> getPublicLessonDetail(@PathVariable Long lessonId) {
        return lessonService.getPublicLessonDetail(lessonId);
    }


}
