package com.muzin.mu.zin.controller.admin;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.admin.AbuseFlagItem;
import com.muzin.mu.zin.dto.admin.AdminModerationCounts;
import com.muzin.mu.zin.dto.admin.AdminReportItem;
import com.muzin.mu.zin.dto.admin.AdminReportResolveRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.adminService.AdminModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 이상행동 감지 / 신고 처리 (계정 정지는 AdminUserController)
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/moderation")
@PreAuthorize("hasRole('ADMIN')")
public class AdminModerationController {

    private final AdminModerationService adminModerationService;

    // 사이드 메뉴 뱃지
    @GetMapping("/counts")
    public ApiRespDto<AdminModerationCounts> counts() {
        return ApiRespDto.ok(adminModerationService.counts());
    }

    // /admin/moderation/flags?open=true&page=0
    @GetMapping("/flags")
    public ApiRespDto<Page<AbuseFlagItem>> flags(
            @RequestParam(defaultValue = "true") boolean open,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiRespDto.ok(adminModerationService.flags(open, pageable));
    }

    @PostMapping("/flags/{flagId}/dismiss")
    public ApiRespDto<Void> dismissFlag(@PathVariable Long flagId, @AuthenticationPrincipal PrincipalUser admin) {
        adminModerationService.dismissFlag(admin.getUserId(), flagId);
        return ApiRespDto.ok("무시했어요. 7일 동안은 같은 이유로 다시 표시되지 않아요.", null);
    }

    // /admin/moderation/reports?open=true&page=0
    @GetMapping("/reports")
    public ApiRespDto<Page<AdminReportItem>> reports(
            @RequestParam(defaultValue = "true") boolean open,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiRespDto.ok(adminModerationService.reports(open, pageable));
    }

    // 문제없음으로 닫기
    @PostMapping("/reports/{reportId}/dismiss")
    public ApiRespDto<Void> dismissReport(
            @PathVariable Long reportId,
            @Valid @RequestBody(required = false) AdminReportResolveRequest req,
            @AuthenticationPrincipal PrincipalUser admin
    ) {
        adminModerationService.resolveReport(admin.getUserId(), reportId, false, req == null ? null : req.memo());
        return ApiRespDto.ok("신고를 닫았어요.", null);
    }

    // 정지 없이 조치 완료로 닫기 (예: 경고, 내용 삭제 요청)
    @PostMapping("/reports/{reportId}/resolve")
    public ApiRespDto<Void> resolveReport(
            @PathVariable Long reportId,
            @Valid @RequestBody(required = false) AdminReportResolveRequest req,
            @AuthenticationPrincipal PrincipalUser admin
    ) {
        adminModerationService.resolveReport(admin.getUserId(), reportId, true, req == null ? null : req.memo());
        return ApiRespDto.ok("조치 완료로 처리했어요.", null);
    }
}
