package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.SetLessonStylesRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.lesson.LessonService;
import com.muzin.mu.zin.service.lesson.LessonStyleService;
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
    public ApiRespDto<?> createLesson(@AuthenticationPrincipal PrincipalUser principalUser) {
        return lessonService.createLesson(principalUser);
    }

    // 아티스트 레슨 조회

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
