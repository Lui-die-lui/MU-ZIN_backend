package com.muzin.mu.zin.controller.admin;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.admin.AdminDashboardResp;
import com.muzin.mu.zin.service.adminService.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    // /admin/dashboard?days=7|30|90 (그 외 값은 30)
    @GetMapping
    public ApiRespDto<AdminDashboardResp> get(@RequestParam(defaultValue = "30") int days) {
        return ApiRespDto.ok(adminDashboardService.get(days));
    }
}
