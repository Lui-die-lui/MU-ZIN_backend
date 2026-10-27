package com.muzin.mu.zin.dto.visit;

// 방문 기록 (브라우저별 하루 1번). 모든 값은 화면에서 보내는 그대로라 서버에서 다시 검사·자름
public record VisitRequest(
        String visitorId,   // 브라우저에 저장한 임의 UUID
        String referrer,    // 다른 사이트에서 들어왔을 때만 (document.referrer)
        String landingPath, // 처음 연 페이지 경로
        String utmSource,   // 광고·캠페인 링크의 utm_source
        Boolean returning   // 이 브라우저가 전에 온 적 있음 (없으면 false)
) {
}
