package com.muzin.mu.zin.controller.admin;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.admin.AdminArtistApplyDetail;
import com.muzin.mu.zin.dto.admin.AdminArtistApplyListItem;
import com.muzin.mu.zin.dto.admin.AdminArtistRejectRequest;
import com.muzin.mu.zin.entity.ArtistStatus;
import com.muzin.mu.zin.service.adminService.AdminArtistApplyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/artist-applications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminArtistApplyController {

    private final AdminArtistApplyService adminArtistApplyService;

    // 목록 조회
    // /admin/artist-applications?status=PENDING&page=0&size=20&sort=submittedDt,desc
    @GetMapping
    public ResponseEntity<ApiRespDto<Page<AdminArtistApplyListItem>>> list(
            @RequestParam(defaultValue = "PENDING") ArtistStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiRespDto.ok(
                adminArtistApplyService.list(status, pageable)
        ));
    }

    // 상세 조회
    @GetMapping("/{artistProfileId}")
    public ResponseEntity<ApiRespDto<AdminArtistApplyDetail>> detail(
            @PathVariable Long artistProfileId
    ) {
        return ResponseEntity.ok(ApiRespDto.ok(
                adminArtistApplyService.detail(artistProfileId)
        ));
    }

    // 승인
    @PostMapping("/{artistProfileId}/approve")
    public ResponseEntity<ApiRespDto<Void>> approve(
            @PathVariable Long artistProfileId
    ) {
        adminArtistApplyService.approve(artistProfileId);
        return ResponseEntity.ok(ApiRespDto.ok(null));
    }

    // 반려
    @PostMapping("/{artistProfileId}/reject")
    public ResponseEntity<ApiRespDto<Void>> reject(
            @PathVariable Long artistProfileId,
            @Valid @RequestBody AdminArtistRejectRequest req
            ) {
        adminArtistApplyService.reject(artistProfileId, req.title(), req.reason());
        return ResponseEntity.ok(ApiRespDto.ok(null));
    }
}
