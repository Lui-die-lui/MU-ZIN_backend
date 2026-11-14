package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.support.SupportRequest;
import com.muzin.mu.zin.dto.support.SupportResponse;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.support.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 운영자 문의 (로그인 필요)
@RestController
@RequiredArgsConstructor
@RequestMapping("/support")
public class SupportController {

    private final SupportService supportService;

    @PostMapping
    public ApiRespDto<SupportResponse> create(@AuthenticationPrincipal PrincipalUser p, @Valid @RequestBody SupportRequest req) {
        return ApiRespDto.ok("문의를 보냈어요. 답변이 오면 알려드릴게요.", supportService.create(p.getUserId(), req.category(), req.content()));
    }

    // 내가 보낸 운영자 문의
    @GetMapping("/me")
    public ApiRespDto<List<SupportResponse>> mine(@AuthenticationPrincipal PrincipalUser p) {
        return ApiRespDto.ok(supportService.getMine(p.getUserId()));
    }
}
