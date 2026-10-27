package com.muzin.mu.zin.service.adminService;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.admin.AdminDashboardResp;
import com.muzin.mu.zin.repository.admin.AdminDashboardRepository;
import com.muzin.mu.zin.repository.admin.ModerationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

// 관리자 대시보드 - 기간(7/30/90일) 동안의 가입·방문·유입 경로·활동 + 바로 앞 같은 길이 기간과 비교
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    public static final Set<Integer> ALLOWED_DAYS = Set.of(7, 30, 90);

    private final AdminDashboardRepository repo;
    private final ModerationRepository moderationRepository;

    public AdminDashboardResp get(int days) {
        int d = ALLOWED_DAYS.contains(days) ? days : 30;
        LocalDate to = TimeDefaults.todayKst();
        LocalDate from = to.minusDays(d - 1L);
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(d - 1L);

        Map<String, Object> status = repo.userStatusCounts();
        ModerationRepository.OpenCounts open = moderationRepository.openCounts();

        AdminDashboardResp.Kpis kpis = new AdminDashboardResp.Kpis(
                repo.totalUsers(),
                repo.newUsers(from, to),
                repo.newUsers(prevFrom, prevTo),
                repo.visitors(from, to),
                repo.visitors(prevFrom, prevTo),
                repo.newVisitors(from, to),
                num(status.get("approved")),
                num(status.get("pending")),
                num(status.get("suspended")),
                open.flags(),
                open.reports(),
                repo.loginFailures(from.atStartOfDay())
        );

        return new AdminDashboardResp(
                d, from, to, kpis,
                repo.daily(from, to),
                repo.sources(from, to),
                repo.referrers(from, to, 8),
                repo.landings(from, to, 6),
                repo.signupMethods(from, to),
                repo.activity(from, to)
        );
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }
}
