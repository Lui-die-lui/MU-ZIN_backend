package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.report.ReportCreateRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.report.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    // 신고하기 (로그인 필요)
    @PostMapping
    public ApiRespDto<Void> create(
            @Valid @RequestBody ReportCreateRequest req,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        reportService.create(principalUser.getUserId(), req);
        return ApiRespDto.ok("신고가 접수됐어요. 관리자가 확인할게요.", null);
    }
}
