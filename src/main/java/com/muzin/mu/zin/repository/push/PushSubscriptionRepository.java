package com.muzin.mu.zin.repository.push;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// 웹 푸시 구독 (V12)
@Repository
@RequiredArgsConstructor
public class PushSubscriptionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public record Subscription(long id, long userId, String endpoint, String p256dh, String auth) { }

    // 같은 endpoint(같은 브라우저)는 한 건만 -> 다시 등록하면 키·계정을 새 값으로 (계정 전환 시 이전 계정에서 떼어 냄)
    public void upsert(long userId, String endpoint, String p256dh, String auth, String userAgent, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO push_subscription (user_id, endpoint, p256dh, auth, user_agent, create_dt, update_dt)
                VALUES (:u, :e, :p, :a, :ua, :now, :now)
                ON CONFLICT (endpoint) DO UPDATE
                   SET user_id = :u, p256dh = :p, auth = :a, user_agent = :ua, failure_count = 0, update_dt = :now
                """, new MapSqlParameterSource("u", userId).addValue("e", endpoint).addValue("p", p256dh)
                .addValue("a", auth).addValue("ua", userAgent).addValue("now", now));
    }

    // 한 유저 기기 수 제한 - 가장 오래 갱신 안 된 것부터 정리
    public void keepLatest(long userId, int max) {
        jdbc.update("""
                DELETE FROM push_subscription WHERE push_subscription_id IN (
                    SELECT push_subscription_id FROM push_subscription WHERE user_id = :u
                    ORDER BY update_dt DESC OFFSET :max)
                """, new MapSqlParameterSource("u", userId).addValue("max", max));
    }

    // 본인 구독만 삭제 (다른 사람 endpoint 를 보내도 지워지지 않게)
    public int deleteOwn(long userId, String endpoint) {
        return jdbc.update("DELETE FROM push_subscription WHERE user_id = :u AND endpoint = :e",
                new MapSqlParameterSource("u", userId).addValue("e", endpoint));
    }

    public List<Subscription> findByUser(long userId) {
        return jdbc.query("SELECT push_subscription_id, user_id, endpoint, p256dh, auth FROM push_subscription WHERE user_id = :u",
                new MapSqlParameterSource("u", userId), (rs, i) -> new Subscription(rs.getLong(1), rs.getLong(2),
                        rs.getString(3), rs.getString(4), rs.getString(5)));
    }

    public boolean exists(long userId, String endpoint) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM push_subscription WHERE user_id = :u AND endpoint = :e",
                new MapSqlParameterSource("u", userId).addValue("e", endpoint), Integer.class);
        return n != null && n > 0;
    }

    public void markSuccess(long id, LocalDateTime now) {
        jdbc.update("UPDATE push_subscription SET failure_count = 0, last_success_dt = :now WHERE push_subscription_id = :id",
                new MapSqlParameterSource("id", id).addValue("now", now));
    }

    // 일시 오류 누적. 너무 많이 연속 실패하면 삭제
    public void markFailure(long id, int deleteAfter) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id).addValue("max", deleteAfter);
        jdbc.update("UPDATE push_subscription SET failure_count = failure_count + 1 WHERE push_subscription_id = :id", p);
        jdbc.update("DELETE FROM push_subscription WHERE push_subscription_id = :id AND failure_count >= :max", p);
    }

    // 푸시 서버가 "더 이상 없는 구독"이라고 답함 (404/410)
    public void deleteById(long id) {
        jdbc.update("DELETE FROM push_subscription WHERE push_subscription_id = :id", new MapSqlParameterSource("id", id));
    }
}
