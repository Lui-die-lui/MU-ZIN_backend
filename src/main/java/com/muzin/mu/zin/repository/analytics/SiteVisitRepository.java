package com.muzin.mu.zin.repository.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class SiteVisitRepository {

    private final NamedParameterJdbcTemplate jdbc;

    // 같은 브라우저·같은 날은 첫 방문만 남김
    public void insertOncePerDay(String visitorId, LocalDate date, String source, String referrerHost,
                                 String utmSource, String landingPath, boolean returning, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO site_visit (visitor_id, visit_date, source, referrer_host, utm_source, landing_path, is_returning, create_dt)
                VALUES (:v, :d, :s, :rh, :utm, :lp, :ret, :now)
                ON CONFLICT (visitor_id, visit_date) DO NOTHING
                """, new MapSqlParameterSource("v", visitorId).addValue("d", date).addValue("s", source)
                .addValue("rh", referrerHost).addValue("utm", utmSource).addValue("lp", landingPath)
                .addValue("ret", returning).addValue("now", now));
    }
}
