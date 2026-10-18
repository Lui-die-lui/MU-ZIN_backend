package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.LessonRecommendRequest;
import com.muzin.mu.zin.dto.lesson.RecommendedLessonResponse;
import com.muzin.mu.zin.service.lesson.LessonRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 음악 취미 찾기 결과 -> 선택 지역의 실제 레슨 (로그인 없이, SecurityConfig 의 GET /lessons/** 공개)
// /lessons/recommend?instIds=19&instIds=21&region1DepthName=서울특별시&region2DepthName=마포구&preferTagIds=4&preferModes=OFFLINE
@RestController
@RequiredArgsConstructor
public class LessonRecommendController {

    private final LessonRecommendService lessonRecommendService;

    @GetMapping("/lessons/recommend")
    public ApiRespDto<List<RecommendedLessonResponse>> recommend(@ModelAttribute LessonRecommendRequest req) {
        return ApiRespDto.ok(lessonRecommendService.recommend(req));
    }
}
