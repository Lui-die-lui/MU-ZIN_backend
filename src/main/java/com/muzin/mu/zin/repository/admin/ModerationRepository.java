package com.muzin.mu.zin.repository.admin;

import com.muzin.mu.zin.dto.admin.AbuseFlagItem;
import com.muzin.mu.zin.dto.admin.AdminReportItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 이상행동 감지(abuse_flag) · 신고(user_report) · 로그인 실패 기록 (V11)
@Repository
@RequiredArgsConstructor
public class ModerationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    // 감지 규칙 쿼리 결과 한 줄: 누가, 얼마나 (extra = 보조 수치, 예: 대화방 수)
    public record Hit(long userId, int metric, int extra) { }

    // 감지 대상에서 빼는 계정: 관리자, 샘플 계정, 이미 정지된 계정
    private static final String NORMAL_USER = """
            u.is_sample = false AND u.suspended_dt IS NULL AND u.withdrawn_dt IS NULL
            AND NOT EXISTS (SELECT 1 FROM user_role ur JOIN role r ON r.role_id = ur.role_id
                            WHERE ur.user_id = u.user_id AND r.role_name = 'ROLE_ADMIN')
            """;

    // ---------- 로그인 실패 ----------

    public void insertLoginFailure(String email, Long userId, LocalDateTime now) {
        jdbc.update("INSERT INTO login_failure_log (email, user_id, create_dt) VALUES (:e, :u, :now)",
                new MapSqlParameterSource("e", email).addValue("u", userId).addValue("now", now));
    }

    public int deleteLoginFailuresBefore(LocalDateTime before) {
        return jdbc.update("DELETE FROM login_failure_log WHERE create_dt < :b", new MapSqlParameterSource("b", before));
    }

    // ---------- 감지 규칙 (since 이후 기록만 셈) ----------

    // 채팅: 보낸 메시지 수 / 보낸 대화방 수
    public List<Hit> chatFlood(LocalDateTime since, int minMessages, int minRooms) {
        return hits("""
                SELECT m.sender_user_id AS user_id, COUNT(*) AS metric, COUNT(DISTINCT m.chat_room_id) AS extra
                FROM chat_message m JOIN users u ON u.user_id = m.sender_user_id
                WHERE m.create_dt >= :since AND %s
                GROUP BY m.sender_user_id
                HAVING COUNT(*) >= :a OR COUNT(DISTINCT m.chat_room_id) >= :b
                """.formatted(NORMAL_USER), since, minMessages, minRooms);
    }

    // 문의: 문의한 아티스트 수 / 같은 내용을 보낸 아티스트 수(최대)
    public List<Hit> inquirySpam(LocalDateTime since, int minArtists, int minSameContent) {
        return hits("""
                WITH recent AS (
                    SELECT i.user_id, i.artist_profile_id, lower(btrim(i.content)) AS body
                    FROM lesson_inquiry i JOIN users u ON u.user_id = i.user_id
                    WHERE i.create_dt >= :since AND %s
                ), same AS (
                    SELECT user_id, MAX(cnt) AS max_same FROM (
                        SELECT user_id, body, COUNT(DISTINCT artist_profile_id) AS cnt FROM recent GROUP BY user_id, body
                    ) t GROUP BY user_id
                )
                SELECT r.user_id, COUNT(DISTINCT r.artist_profile_id) AS metric, MAX(s.max_same) AS extra
                FROM recent r JOIN same s ON s.user_id = r.user_id
                GROUP BY r.user_id
                HAVING COUNT(DISTINCT r.artist_profile_id) >= :a OR MAX(s.max_same) >= :b
                """.formatted(NORMAL_USER), since, minArtists, minSameContent);
    }

    // 예약 요청 수
    public List<Hit> reservationSpam(LocalDateTime since, int minRequests) {
        return hits("""
                SELECT r.user_id, COUNT(*) AS metric, COUNT(DISTINCT r.lesson_id) AS extra
                FROM lesson_reservation r JOIN users u ON u.user_id = r.user_id
                WHERE r.requested_dt >= :since AND %s
                GROUP BY r.user_id HAVING COUNT(*) >= :a
                """.formatted(NORMAL_USER), since, minRequests, 0);
    }

    // 수강생이 직접 취소한 예약 수
    public List<Hit> reservationCancel(LocalDateTime since, int minCancels) {
        return hits("""
                SELECT r.user_id, COUNT(*) AS metric, 0 AS extra
                FROM lesson_reservation r JOIN users u ON u.user_id = r.user_id
                WHERE r.canceled_by = 'USER' AND r.canceled_dt >= :since AND %s
                GROUP BY r.user_id HAVING COUNT(*) >= :a
                """.formatted(NORMAL_USER), since, minCancels, 0);
    }

    // 이 계정으로 들어온 로그인 실패 수 (계정 주인이 아니라 공격 대상)
    public List<Hit> loginFailure(LocalDateTime since, int minFailures) {
        return hits("""
                SELECT f.user_id, COUNT(*) AS metric, 0 AS extra
                FROM login_failure_log f JOIN users u ON u.user_id = f.user_id
                WHERE f.create_dt >= :since AND %s
                GROUP BY f.user_id HAVING COUNT(*) >= :a
                """.formatted(NORMAL_USER), since, minFailures, 0);
    }

    // 짧은 시간에 쓴 리뷰 수
    public List<Hit> reviewBurst(LocalDateTime since, int minReviews) {
        return hits("""
                SELECT v.review_user_id AS user_id, COUNT(*) AS metric, COUNT(DISTINCT v.artist_profile_id) AS extra
                FROM review v JOIN users u ON u.user_id = v.review_user_id
                WHERE v.delete_dt IS NULL AND v.create_dt >= :since AND %s
                GROUP BY v.review_user_id HAVING COUNT(*) >= :a
                """.formatted(NORMAL_USER), since, minReviews, 0);
    }

    // 별점 1점을 준 아티스트 수
    public List<Hit> reviewLowRating(LocalDateTime since, int minArtists) {
        return hits("""
                SELECT v.review_user_id AS user_id, COUNT(DISTINCT v.artist_profile_id) AS metric, COUNT(*) AS extra
                FROM review v JOIN users u ON u.user_id = v.review_user_id
                WHERE v.delete_dt IS NULL AND v.rating = 1 AND v.create_dt >= :since AND %s
                GROUP BY v.review_user_id HAVING COUNT(DISTINCT v.artist_profile_id) >= :a
                """.formatted(NORMAL_USER), since, minArtists, 0);
    }

    // 처리 안 된 신고를 한 서로 다른 사람 수
    public List<Hit> reported(int minReporters) {
        return hits("""
                SELECT p.target_user_id AS user_id, COUNT(DISTINCT p.reporter_user_id) AS metric, COUNT(*) AS extra
                FROM user_report p JOIN users u ON u.user_id = p.target_user_id
                WHERE p.status = 'OPEN' AND %s
                GROUP BY p.target_user_id HAVING COUNT(DISTINCT p.reporter_user_id) >= :a
                """.formatted(NORMAL_USER), null, minReporters, 0);
    }

    private List<Hit> hits(String sql, LocalDateTime since, int a, int b) {
        MapSqlParameterSource p = new MapSqlParameterSource("since", since).addValue("a", a).addValue("b", b);
        return jdbc.query(sql, p, (rs, i) -> new Hit(rs.getLong("user_id"), rs.getInt("metric"), rs.getInt("extra")));
    }

    // ---------- 감지 결과 저장 ----------

    // 열린 건이 있으면 수치만 갱신, 없으면 새로 (최근 무시한 건은 suppressSince 이후면 다시 띄우지 않음)
    // @return 새로 띄웠으면 true
    public boolean upsertFlag(long userId, String rule, String detail, int metric, LocalDateTime now, LocalDateTime suppressSince) {
        MapSqlParameterSource p = new MapSqlParameterSource("u", userId).addValue("r", rule).addValue("d", detail)
                .addValue("m", metric).addValue("now", now).addValue("sup", suppressSince);
        int updated = jdbc.update("""
                UPDATE abuse_flag SET detail = :d, metric = :m, last_detected_dt = :now
                WHERE user_id = :u AND rule = :r AND status = 'OPEN'
                """, p);
        if (updated > 0) return false;
        return jdbc.update("""
                INSERT INTO abuse_flag (user_id, rule, detail, metric, status, detected_dt, last_detected_dt)
                SELECT :u, :r, :d, :m, 'OPEN', :now, :now
                WHERE NOT EXISTS (SELECT 1 FROM abuse_flag
                                  WHERE user_id = :u AND rule = :r AND status = 'DISMISSED' AND resolved_dt >= :sup)
                ON CONFLICT (user_id, rule) WHERE status = 'OPEN' DO NOTHING
                """, p) > 0;
    }

    public Page<AbuseFlagItem> flags(boolean open, Pageable pageable) {
        String where = open ? "f.status = 'OPEN'" : "f.status <> 'OPEN'";
        String order = open ? "f.last_detected_dt DESC" : "f.resolved_dt DESC NULLS LAST";
        List<AbuseFlagItem> rows = jdbc.query("""
                SELECT f.*,
                       CASE WHEN u.withdrawn_dt IS NOT NULL THEN '' ELSE u.email END AS email,
                       CASE WHEN u.withdrawn_dt IS NOT NULL THEN '탈퇴한 회원' ELSE u.username END AS username,
                       CASE WHEN u.withdrawn_dt IS NOT NULL THEN NULL ELSE u.profile_img_url END AS profile_img_url,
                       u.suspended_dt,
                       CASE WHEN a.withdrawn_dt IS NOT NULL THEN '탈퇴한 회원' ELSE a.username END AS resolved_by_name
                FROM abuse_flag f
                JOIN users u ON u.user_id = f.user_id
                LEFT JOIN users a ON a.user_id = f.resolved_by
                WHERE %s ORDER BY %s, f.abuse_flag_id DESC LIMIT :lim OFFSET :off
                """.formatted(where, order), paging(pageable), (rs, i) -> new AbuseFlagItem(
                rs.getLong("abuse_flag_id"),
                rs.getLong("user_id"),
                rs.getString("email"),
                rs.getString("username"),
                rs.getString("profile_img_url"),
                rs.getTimestamp("suspended_dt") != null,
                rs.getString("rule"),
                rs.getString("detail"),
                rs.getInt("metric"),
                rs.getString("status"),
                dt(rs, "detected_dt"),
                dt(rs, "last_detected_dt"),
                rs.getString("resolved_by_name"),
                dt(rs, "resolved_dt")
        ));
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM abuse_flag f WHERE " + where,
                new MapSqlParameterSource(), Long.class);
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    public Optional<String> flagStatus(long flagId) {
        return jdbc.queryForList("SELECT status FROM abuse_flag WHERE abuse_flag_id = :id",
                new MapSqlParameterSource("id", flagId), String.class).stream().findFirst();
    }

    public void dismissFlag(long flagId, long adminId, LocalDateTime now) {
        jdbc.update("""
                UPDATE abuse_flag SET status = 'DISMISSED', resolved_by = :a, resolved_dt = :now
                WHERE abuse_flag_id = :id AND status = 'OPEN'
                """, new MapSqlParameterSource("id", flagId).addValue("a", adminId).addValue("now", now));
    }

    // ---------- 신고 ----------

    public boolean reportExists(long reporterId, String targetType, long targetRefId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_report
                WHERE reporter_user_id = :r AND target_type = :t AND target_ref_id = :id
                """, new MapSqlParameterSource("r", reporterId).addValue("t", targetType).addValue("id", targetRefId), Integer.class);
        return n != null && n > 0;
    }

    public int reportsSince(long reporterId, LocalDateTime since) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM user_report WHERE reporter_user_id = :r AND create_dt >= :s",
                new MapSqlParameterSource("r", reporterId).addValue("s", since), Integer.class);
        return n == null ? 0 : n;
    }

    public void insertReport(long reporterId, long targetUserId, String targetType, long targetRefId,
                             String reason, String detail, String snapshot, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO user_report (reporter_user_id, target_user_id, target_type, target_ref_id, reason, detail,
                                         content_snapshot, status, create_dt)
                VALUES (:r, :tu, :t, :id, :reason, :detail, :snap, 'OPEN', :now)
                """, new MapSqlParameterSource("r", reporterId).addValue("tu", targetUserId).addValue("t", targetType)
                .addValue("id", targetRefId).addValue("reason", reason).addValue("detail", detail)
                .addValue("snap", snapshot).addValue("now", now));
    }

    public Page<AdminReportItem> reports(boolean open, Pageable pageable) {
        String where = open ? "p.status = 'OPEN'" : "p.status <> 'OPEN'";
        String order = open ? "p.create_dt DESC" : "p.resolved_dt DESC NULLS LAST";
        List<AdminReportItem> rows = jdbc.query("""
                SELECT p.*,
                       CASE WHEN r.withdrawn_dt IS NOT NULL THEN '탈퇴한 회원' ELSE r.username END AS reporter_name,
                       CASE WHEN r.withdrawn_dt IS NOT NULL THEN '' ELSE r.email END AS reporter_email,
                       CASE WHEN t.withdrawn_dt IS NOT NULL THEN '탈퇴한 회원' ELSE t.username END AS target_name,
                       CASE WHEN t.withdrawn_dt IS NOT NULL THEN '' ELSE t.email END AS target_email,
                       CASE WHEN t.withdrawn_dt IS NOT NULL THEN NULL ELSE t.profile_img_url END AS target_img,
                       t.suspended_dt AS target_suspended_dt,
                       CASE WHEN a.withdrawn_dt IS NOT NULL THEN '탈퇴한 회원' ELSE a.username END AS resolved_by_name,
                       (SELECT COUNT(*) FROM user_report o
                        WHERE o.target_user_id = p.target_user_id AND o.status = 'OPEN') AS target_open_reports
                FROM user_report p
                JOIN users r ON r.user_id = p.reporter_user_id
                JOIN users t ON t.user_id = p.target_user_id
                LEFT JOIN users a ON a.user_id = p.resolved_by
                WHERE %s ORDER BY %s, p.report_id DESC LIMIT :lim OFFSET :off
                """.formatted(where, order), paging(pageable), (rs, i) -> new AdminReportItem(
                rs.getLong("report_id"),
                rs.getLong("reporter_user_id"),
                rs.getString("reporter_name"),
                rs.getString("reporter_email"),
                rs.getLong("target_user_id"),
                rs.getString("target_name"),
                rs.getString("target_email"),
                rs.getString("target_img"),
                rs.getTimestamp("target_suspended_dt") != null,
                rs.getInt("target_open_reports"),
                rs.getString("target_type"),
                rs.getLong("target_ref_id"),
                rs.getString("reason"),
                rs.getString("detail"),
                rs.getString("content_snapshot"),
                rs.getString("status"),
                rs.getString("admin_memo"),
                rs.getString("resolved_by_name"),
                dt(rs, "create_dt"),
                dt(rs, "resolved_dt")
        ));
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM user_report p WHERE " + where,
                new MapSqlParameterSource(), Long.class);
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    public Optional<String> reportStatus(long reportId) {
        return jdbc.queryForList("SELECT status FROM user_report WHERE report_id = :id",
                new MapSqlParameterSource("id", reportId), String.class).stream().findFirst();
    }

    public void resolveReport(long reportId, String status, String memo, long adminId, LocalDateTime now) {
        jdbc.update("""
                UPDATE user_report SET status = :s, admin_memo = :m, resolved_by = :a, resolved_dt = :now
                WHERE report_id = :id AND status = 'OPEN'
                """, new MapSqlParameterSource("id", reportId).addValue("s", status).addValue("m", memo)
                .addValue("a", adminId).addValue("now", now));
    }

    // 계정을 정지하면 그 유저에게 열려 있던 감지·신고를 "조치함"으로 닫음
    public void closeAllOpenFor(long userId, long adminId, LocalDateTime now) {
        MapSqlParameterSource p = new MapSqlParameterSource("u", userId).addValue("a", adminId).addValue("now", now);
        jdbc.update("""
                UPDATE abuse_flag SET status = 'ACTIONED', resolved_by = :a, resolved_dt = :now
                WHERE user_id = :u AND status = 'OPEN'
                """, p);
        jdbc.update("""
                UPDATE user_report SET status = 'ACTIONED', admin_memo = COALESCE(admin_memo, '계정 정지'),
                       resolved_by = :a, resolved_dt = :now
                WHERE target_user_id = :u AND status = 'OPEN'
                """, p);
    }

    public record OpenCounts(long flags, long reports) { }

    public OpenCounts openCounts() {
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM abuse_flag WHERE status = 'OPEN') AS flags,
                       (SELECT COUNT(*) FROM user_report WHERE status = 'OPEN') AS reports
                """, new MapSqlParameterSource(), (rs, i) -> new OpenCounts(rs.getLong("flags"), rs.getLong("reports")));
    }

    private static MapSqlParameterSource paging(Pageable pageable) {
        return new MapSqlParameterSource("lim", pageable.getPageSize()).addValue("off", pageable.getOffset());
    }

    private static LocalDateTime dt(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }
}
