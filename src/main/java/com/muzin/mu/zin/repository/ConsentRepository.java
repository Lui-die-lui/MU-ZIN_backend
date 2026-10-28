package com.muzin.mu.zin.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

// 약관 동의 기록 (V13)
@Repository
@RequiredArgsConstructor
public class ConsentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public record Versions(String terms, String privacy) { }

    public void insert(long userId, String termsVersion, String privacyVersion, boolean ageOver14, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO user_consent (user_id, terms_version, privacy_version, age_over_14, agreed_dt)
                VALUES (:u, :t, :p, :a, :now)
                """, new MapSqlParameterSource("u", userId).addValue("t", termsVersion).addValue("p", privacyVersion)
                .addValue("a", ageOver14).addValue("now", now));
    }

    // 가장 최근에 동의한 버전 (동의한 적 없으면 비어 있음)
    public Optional<Versions> latest(long userId) {
        return jdbc.query("""
                SELECT terms_version, privacy_version FROM user_consent
                WHERE user_id = :u AND age_over_14 = true
                ORDER BY agreed_dt DESC, consent_id DESC LIMIT 1
                """, new MapSqlParameterSource("u", userId),
                (rs, i) -> new Versions(rs.getString(1), rs.getString(2))).stream().findFirst();
    }
}
