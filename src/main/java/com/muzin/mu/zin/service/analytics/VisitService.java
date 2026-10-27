package com.muzin.mu.zin.service.analytics;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.visit.VisitRequest;
import com.muzin.mu.zin.repository.analytics.SiteVisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/*
 * 방문 기록 (관리자 대시보드 유입 분석용)
 *  - 브라우저에 저장한 임의 UUID 로 하루 1건만. IP·쿠키·회원 ID 는 저장하지 않음
 *  - 들어온 사이트는 주소 전체가 아니라 도메인만 남김 (검색어 등 개인 정보가 섞이지 않게)
 */
@Service
@RequiredArgsConstructor
public class VisitService {

    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern BOT = Pattern.compile("bot|crawl|spider|slurp|headless|lighthouse|preview|facebookexternalhit", Pattern.CASE_INSENSITIVE);
    private static final Pattern UTM = Pattern.compile("^[\\w.\\-]{1,50}$");

    // 앞에서부터 먼저 맞는 것 (blog.naver.com 은 검색이 아니라 SNS 로)
    private static final List<String> SOCIAL = List.of("instagram.", "facebook.", "fb.", "t.co", "twitter.", "x.com",
            "youtube.", "youtu.be", "threads.", "tiktok.", "linkedin.", "kakao.", "band.us", "blog.naver.", "cafe.naver.",
            "blog.daum.", "cafe.daum.", "everytime.", "reddit.", "discord.");
    private static final List<String> SEARCH = List.of("google.", "naver.", "daum.", "bing.", "yahoo.", "duckduckgo.",
            "baidu.", "yandex.", "ecosia.", "zum.");
    // 우리 사이트에서 다시 들어온 경우는 "직접 방문"으로
    private static final List<String> SELF = List.of("mu-zin.com", "localhost", "127.0.0.1");

    private final SiteVisitRepository siteVisitRepository;

    public void record(VisitRequest req, String userAgent) {
        if (req == null || req.visitorId() == null || !UUID.matcher(req.visitorId()).matches()) return;
        if (userAgent == null || userAgent.isBlank() || BOT.matcher(userAgent).find()) return;

        String host = referrerHost(req.referrer());
        String utm = req.utmSource() == null ? null : req.utmSource().trim().toLowerCase(Locale.ROOT);
        if (utm != null && !UTM.matcher(utm).matches()) utm = null;

        siteVisitRepository.insertOncePerDay(
                req.visitorId().toLowerCase(Locale.ROOT),
                TimeDefaults.todayKst(),
                classify(host, utm),
                host,
                utm,
                landingPath(req.landingPath()),
                Boolean.TRUE.equals(req.returning()),
                TimeDefaults.nowKst()
        );
    }

    static String classify(String host, String utm) {
        if (utm != null) return "CAMPAIGN";
        if (host == null) return "DIRECT";
        if (SOCIAL.stream().anyMatch(t -> matches(host, t))) return "SOCIAL";
        if (SEARCH.stream().anyMatch(t -> matches(host, t))) return "SEARCH";
        return "REFERRAL";
    }

    // "google." 처럼 점으로 끝나면 도메인 이름 앞부분(m.google.co.kr 도 맞음), 아니면 그 도메인이나 하위 도메인만
    // (pinterest.com 이 t.co 로 잡히지 않게)
    private static boolean matches(String host, String token) {
        if (token.endsWith(".")) return ("." + host).contains("." + token);
        return host.equals(token) || host.endsWith("." + token);
    }

    // https://m.search.naver.com/search?q=... -> m.search.naver.com (www. 는 뗌, 우리 사이트면 null)
    static String referrerHost(String referrer) {
        if (referrer == null || referrer.isBlank()) return null;
        try {
            String host = URI.create(referrer.trim()).getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            final String h = host;
            if (SELF.stream().anyMatch(s -> h.equals(s) || h.endsWith("." + s))) return null;
            return h.length() > 100 ? h.substring(0, 100) : h;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // 쿼리·해시는 버리고 경로만. 숫자 ID 는 :id 로 묶어서 같은 종류 페이지끼리 모이게 (/artists/12 -> /artists/:id)
    static String landingPath(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/")) return null;
        String p = path.split("[?#]", 2)[0].replaceAll("/\\d+(?=/|$)", "/:id");
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p.length() > 200 ? p.substring(0, 200) : p;
    }
}
