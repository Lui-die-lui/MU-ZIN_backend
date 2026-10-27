package com.muzin.mu.zin.repository.admin;

import com.muzin.mu.zin.dto.admin.AdminDashboardResp.Activity;
import com.muzin.mu.zin.dto.admin.AdminDashboardResp.DailyPoint;
import com.muzin.mu.zin.dto.admin.AdminDashboardResp.NameCount;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// 관리자 대시보드 집계. 기간은 [from, to] 날짜 (to 포함), 시각 비교는 [from 0시, to+1일 0시)
@Repository
@RequiredArgsConstructor
public class AdminDashboardRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static MapSqlParameterSource range(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource("fromD", from).addValue("toD", to)
                .addValue("fromT", from.atStartOfDay()).addValue("toT", to.plusDays(1).atStartOfDay());
    }

    private long count(String sql, MapSqlParameterSource p) {
        Long n = jdbc.queryForObject(sql, p, Long.class);
        return n == null ? 0 : n;
    }

    // ---------- 회원 ----------

    public long totalUsers() {
        return count("SELECT COUNT(*) FROM users WHERE is_sample = false", new MapSqlParameterSource());
    }

    public long newUsers(LocalDate from, LocalDate to) {
        return count("SELECT COUNT(*) FROM users WHERE is_sample = false AND create_dt >= :fromT AND create_dt < :toT",
                range(from, to));
    }

    // 승인된 아티스트 / 심사 대기 / 정지된 계정 수를 한 번에
    public Map<String, Object> userStatusCounts() {
        return jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE artist_status = 'APPROVED') AS approved,
                       COUNT(*) FILTER (WHERE artist_status = 'PENDING')  AS pending,
                       COUNT(*) FILTER (WHERE suspended_dt IS NOT NULL)   AS suspended
                FROM users WHERE is_sample = false
                """, new MapSqlParameterSource());
    }

    // 가입 방식: 소셜 연결이 있으면 그 제공자, 없으면 EMAIL
    public List<NameCount> signupMethods(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT COALESCE((SELECT upper(o.provider) FROM oauth2_user o WHERE o.user_id = u.user_id
                                 ORDER BY o.oauth2_user_id LIMIT 1), 'EMAIL') AS name, COUNT(*) AS cnt
                FROM users u
                WHERE u.is_sample = false AND u.create_dt >= :fromT AND u.create_dt < :toT
                GROUP BY 1 ORDER BY cnt DESC
                """, range(from, to), (rs, i) -> new NameCount(rs.getString("name"), rs.getLong("cnt")));
    }

    // ---------- 방문 ----------

    public long visitors(LocalDate from, LocalDate to) {
        return count("SELECT COUNT(DISTINCT visitor_id) FROM site_visit WHERE visit_date BETWEEN :fromD AND :toD",
                range(from, to));
    }

    // 이 기간에 처음 온 브라우저
    public long newVisitors(LocalDate from, LocalDate to) {
        return count("""
                SELECT COUNT(DISTINCT visitor_id) FROM site_visit
                WHERE visit_date BETWEEN :fromD AND :toD AND is_returning = false
                """, range(from, to));
    }

    // 날짜별 방문자 / 새 방문자 / 가입자 (빈 날짜도 0으로 채움)
    public List<DailyPoint> daily(LocalDate from, LocalDate to) {
        return jdbc.query("""
                WITH d AS (SELECT CAST(g AS date) AS day
                           FROM generate_series(CAST(:fromD AS date), CAST(:toD AS date), INTERVAL '1 day') g),
                v AS (SELECT visit_date AS day, COUNT(*) AS visitors, COUNT(*) FILTER (WHERE is_returning = false) AS fresh
                      FROM site_visit WHERE visit_date BETWEEN :fromD AND :toD GROUP BY visit_date),
                s AS (SELECT CAST(create_dt AS date) AS day, COUNT(*) AS signups
                      FROM users WHERE is_sample = false AND create_dt >= :fromT AND create_dt < :toT
                      GROUP BY 1)
                SELECT d.day, COALESCE(v.visitors, 0) AS visitors, COALESCE(v.fresh, 0) AS fresh,
                       COALESCE(s.signups, 0) AS signups
                FROM d LEFT JOIN v ON v.day = d.day LEFT JOIN s ON s.day = d.day
                ORDER BY d.day
                """, range(from, to), (rs, i) -> new DailyPoint(
                rs.getDate("day").toLocalDate(), rs.getLong("visitors"), rs.getLong("fresh"), rs.getLong("signups")));
    }

    // 유입 경로별 방문 수 (방문 건수 기준 = 브라우저·날짜별 1건)
    public List<NameCount> sources(LocalDate from, LocalDate to) {
        return nameCounts("source", null, from, to, 10);
    }

    public List<NameCount> referrers(LocalDate from, LocalDate to, int limit) {
        return nameCounts("referrer_host", "referrer_host IS NOT NULL", from, to, limit);
    }

    public List<NameCount> landings(LocalDate from, LocalDate to, int limit) {
        return nameCounts("landing_path", "landing_path IS NOT NULL", from, to, limit);
    }

    // col / extraWhere 는 이 클래스 안의 고정 문자열만 넘김 (사용자 입력 아님)
    private List<NameCount> nameCounts(String col, String extraWhere, LocalDate from, LocalDate to, int limit) {
        String where = "visit_date BETWEEN :fromD AND :toD" + (extraWhere == null ? "" : " AND " + extraWhere);
        return jdbc.query("SELECT " + col + " AS name, COUNT(*) AS cnt FROM site_visit WHERE " + where +
                        " GROUP BY " + col + " ORDER BY cnt DESC, name LIMIT :lim",
                range(from, to).addValue("lim", limit),
                (rs, i) -> new NameCount(rs.getString("name"), rs.getLong("cnt")));
    }

    // ---------- 활동 ----------

    public Activity activity(LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM lesson_reservation WHERE requested_dt >= :fromT AND requested_dt < :toT) AS req,
                  (SELECT COUNT(*) FROM lesson_reservation WHERE completed_dt >= :fromT AND completed_dt < :toT) AS done,
                  (SELECT COUNT(*) FROM lesson_reservation WHERE canceled_dt >= :fromT AND canceled_dt < :toT) AS canceled,
                  (SELECT COUNT(*) FROM chat_message WHERE create_dt >= :fromT AND create_dt < :toT) AS chats,
                  (SELECT COUNT(*) FROM lesson_inquiry WHERE create_dt >= :fromT AND create_dt < :toT) AS inquiries,
                  (SELECT COUNT(*) FROM review WHERE delete_dt IS NULL AND create_dt >= :fromT AND create_dt < :toT) AS reviews
                """, range(from, to), (rs, i) -> new Activity(rs.getLong("req"), rs.getLong("done"),
                rs.getLong("canceled"), rs.getLong("chats"), rs.getLong("inquiries"), rs.getLong("reviews")));
    }

    public long loginFailures(LocalDateTime since) {
        return count("SELECT COUNT(*) FROM login_failure_log WHERE create_dt >= :s", new MapSqlParameterSource("s", since));
    }
}
