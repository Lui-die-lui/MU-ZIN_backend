package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.LessonRecurrenceResponse;
import com.muzin.mu.zin.dto.lesson.LessonRecurrenceUpsertRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.lesson.recurrence.LessonRecurrenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/lessons/me")
public class LessonRecurrenceController {

    private final LessonRecurrenceService lessonRecurrenceService;

    @GetMapping("/{lessonId}/recurrence")
    public ApiRespDto<LessonRecurrenceResponse> getRule(@PathVariable Long lessonId,
                                                        @AuthenticationPrincipal PrincipalUser principalUser) {
        return lessonRecurrenceService.getRule(lessonId, principalUser);
    }

    @PutMapping("/{lessonId}/recurrence")
    ApiRespDto<LessonRecurrenceResponse> upsertRule(
            @PathVariable Long lessonId,
            @RequestBody LessonRecurrenceUpsertRequest req,
            @AuthenticationPrincipal PrincipalUser principalUser
            ) {
        return lessonRecurrenceService.upsertRuleAndMaterialize(lessonId, req, principalUser);
    }


}
