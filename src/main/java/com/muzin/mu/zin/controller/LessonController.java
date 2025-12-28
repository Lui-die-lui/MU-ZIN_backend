package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.LessonCreateRequest;
import com.muzin.mu.zin.dto.lesson.LessonUpdateRequest;
import com.muzin.mu.zin.dto.lesson.SetLessonStylesRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.lesson.LessonService;
import com.muzin.mu.zin.service.lesson.LessonStyleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
    @DeleteMapping("/{lessonId}")
    public ApiRespDto<?> deleteLesson(@PathVariable Long lessonId, @AuthenticationPrincipal PrincipalUser principalUser) {
        return lessonService.deleteLesson(lessonId, principalUser);
    }

    // 아티스트 레슨 조회
    @GetMapping("/me")
    public ApiRespDto<?> getArtistLesson(@AuthenticationPrincipal PrincipalUser principalUser) {
        return lessonService.getArtistLesson(principalUser);
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

}
