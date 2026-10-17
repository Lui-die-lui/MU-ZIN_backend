package com.muzin.mu.zin.controller.admin;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.admin.AdminSuspendRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.adminService.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    // 계정 정지
    @PostMapping("/{userId}/suspend")
    public ApiRespDto<Void> suspend(
            @PathVariable Long userId,
            @Valid @RequestBody AdminSuspendRequest req,
            @AuthenticationPrincipal PrincipalUser admin
    ) {
        adminUserService.suspend(admin.getUserId(), userId, req.reason());
        return ApiRespDto.ok("계정이 정지되었습니다.", null);
    }

    // 계정 정지 해제
    @PostMapping("/{userId}/unsuspend")
    public ApiRespDto<Void> unsuspend(
            @PathVariable Long userId,
            @AuthenticationPrincipal PrincipalUser admin
    ) {
        adminUserService.unsuspend(admin.getUserId(), userId);
        return ApiRespDto.ok("계정 정지가 해제되었습니다.", null);
    }
}
