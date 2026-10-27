package com.muzin.mu.zin.dto.admin;

import java.time.LocalDate;
import java.util.List;

// 관리자 대시보드 (기간: from ~ to, 비교 기간: 바로 앞 같은 길이). 샘플 계정은 회원 수치에서 제외
public record AdminDashboardResp(
        int days,
        LocalDate from,
        LocalDate to,
        Kpis kpis,
        List<DailyPoint> daily,
        List<NameCount> sources,       // 유입 경로 (DIRECT / SEARCH / SOCIAL / REFERRAL / CAMPAIGN)
        List<NameCount> referrers,     // 들어온 사이트 상위
        List<NameCount> landings,      // 처음 연 페이지 상위
        List<NameCount> signupMethods, // 가입 방식 (EMAIL / GOOGLE / KAKAO / ...)
        Activity activity
) {
    public record Kpis(
            long totalUsers,
            long newUsers,
            long newUsersPrev,
            long visitors,       // 기간 중 방문한 브라우저 수 (중복 제외)
            long visitorsPrev,
            long newVisitors,    // 처음 온 브라우저 수
            long approvedArtists,
            long pendingArtists,
            long suspendedUsers,
            long openFlags,
            long openReports,
            long loginFailures
    ) { }

    public record DailyPoint(LocalDate date, long visitors, long newVisitors, long signups) { }

    public record NameCount(String name, long count) { }

    public record Activity(
            long reservationRequests,
            long reservationsCompleted,
            long reservationsCanceled,
            long chatMessages,
            long inquiries,
            long reviews
    ) { }
}
