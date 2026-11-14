package com.muzin.mu.zin.controller.admin;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.support.AdminSupportItem;
import com.muzin.mu.zin.dto.support.SupportReplyRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.support.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// 운영자 문의 답변
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/support")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSupportController {

    private final SupportService supportService;

    // /admin/support?open=true&page=0
    @GetMapping
    public ApiRespDto<Page<AdminSupportItem>> list(
            @RequestParam(defaultValue = "true") boolean open,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiRespDto.ok(supportService.adminList(open, pageable));
    }

    // 사이드 메뉴 뱃지
    @GetMapping("/open-count")
    public ApiRespDto<Map<String, Long>> openCount() {
        return ApiRespDto.ok(Map.of("openCount", supportService.countOpen()));
    }

    @PostMapping("/{supportId}/reply")
    public ApiRespDto<AdminSupportItem> reply(
            @PathVariable Long supportId,
            @Valid @RequestBody SupportReplyRequest req,
            @AuthenticationPrincipal PrincipalUser admin
    ) {
        return ApiRespDto.ok("답변했어요. 문의한 분에게 알림이 가요.", supportService.reply(admin.getUserId(), supportId, req.content()));
    }
}
