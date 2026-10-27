package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.visit.VisitRequest;
import com.muzin.mu.zin.service.analytics.VisitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/visits")
public class VisitController {

    private final VisitService visitService;

    // 방문 기록 (로그인 없이). 잘못된 값은 조용히 버리고 항상 성공으로 응답
    @PostMapping
    public ApiRespDto<Void> record(
            @RequestBody(required = false) VisitRequest req,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        visitService.record(req, userAgent);
        return ApiRespDto.ok(null);
    }
}
