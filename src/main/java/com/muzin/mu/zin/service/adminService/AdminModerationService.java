package com.muzin.mu.zin.service.adminService;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.admin.AbuseFlagItem;
import com.muzin.mu.zin.dto.admin.AdminModerationCounts;
import com.muzin.mu.zin.dto.admin.AdminReportItem;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.admin.ModerationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리자 이상행동 화면 - 감지된 유저 / 신고 목록, 무시·처리
// 계정 정지는 회원 관리와 같은 API(/admin/users/{id}/suspend)를 쓰고, 정지하면 열린 감지·신고가 함께 닫힘
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminModerationService {

    private final ModerationRepository moderationRepository;

    @Transactional(readOnly = true)
    public AdminModerationCounts counts() {
        ModerationRepository.OpenCounts c = moderationRepository.openCounts();
        return new AdminModerationCounts(c.flags(), c.reports());
    }

    @Transactional(readOnly = true)
    public Page<AbuseFlagItem> flags(boolean open, Pageable pageable) {
        return moderationRepository.flags(open, pageable);
    }

    public void dismissFlag(long adminId, long flagId) {
        String status = moderationRepository.flagStatus(flagId)
                .orElseThrow(() -> ApiException.notFound("감지 기록을 찾을 수 없습니다."));
        if (!"OPEN".equals(status)) throw ApiException.conflict("이미 처리된 건입니다.");
        moderationRepository.dismissFlag(flagId, adminId, TimeDefaults.nowKst());
        log.info("이상행동 무시: flag={} by={}", flagId, adminId);
    }

    @Transactional(readOnly = true)
    public Page<AdminReportItem> reports(boolean open, Pageable pageable) {
        return moderationRepository.reports(open, pageable);
    }

    // actioned: true = 조치함(정지 외 조치, 예: 내용 삭제 요청) / false = 문제없음(무시)
    public void resolveReport(long adminId, long reportId, boolean actioned, String memo) {
        String status = moderationRepository.reportStatus(reportId)
                .orElseThrow(() -> ApiException.notFound("신고를 찾을 수 없습니다."));
        if (!"OPEN".equals(status)) throw ApiException.conflict("이미 처리된 신고입니다.");
        String m = memo == null || memo.isBlank() ? null : memo.trim();
        moderationRepository.resolveReport(reportId, actioned ? "ACTIONED" : "DISMISSED", m, adminId, TimeDefaults.nowKst());
        log.info("신고 처리: report={} actioned={} by={}", reportId, actioned, adminId);
    }
}
