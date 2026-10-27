package com.muzin.mu.zin.service.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisitServiceTest {

    @Test
    @DisplayName("들어온 주소는 도메인만 남기고 www 는 뗌, 우리 사이트는 버림")
    void referrerHost() {
        assertThat(VisitService.referrerHost("https://m.search.naver.com/search.naver?query=드럼레슨")).isEqualTo("m.search.naver.com");
        assertThat(VisitService.referrerHost("https://www.google.com/")).isEqualTo("google.com");
        assertThat(VisitService.referrerHost("https://mu-zin.com/artists/3")).isNull();
        assertThat(VisitService.referrerHost("http://localhost:5173/")).isNull();
        assertThat(VisitService.referrerHost("not a url")).isNull();
        assertThat(VisitService.referrerHost("")).isNull();
    }

    @Test
    @DisplayName("유입 경로 분류: 캠페인 > 직접 > SNS > 검색 > 그 외 사이트")
    void classify() {
        assertThat(VisitService.classify("instagram.com", "insta_ad")).isEqualTo("CAMPAIGN");
        assertThat(VisitService.classify(null, null)).isEqualTo("DIRECT");
        assertThat(VisitService.classify("l.instagram.com", null)).isEqualTo("SOCIAL");
        assertThat(VisitService.classify("t.co", null)).isEqualTo("SOCIAL");
        assertThat(VisitService.classify("m.blog.naver.com", null)).isEqualTo("SOCIAL");
        assertThat(VisitService.classify("m.search.naver.com", null)).isEqualTo("SEARCH");
        assertThat(VisitService.classify("google.co.kr", null)).isEqualTo("SEARCH");
        // 짧은 도메인이 다른 도메인 안에 들어 있어도 잘못 잡지 않음
        assertThat(VisitService.classify("pinterest.com", null)).isEqualTo("REFERRAL");
        assertThat(VisitService.classify("netflix.com", null)).isEqualTo("REFERRAL");
    }

    @Test
    @DisplayName("처음 연 페이지: 쿼리 제거, 숫자 ID 는 :id 로 묶음")
    void landingPath() {
        assertThat(VisitService.landingPath("/artists/12?tab=review#top")).isEqualTo("/artists/:id");
        assertThat(VisitService.landingPath("/lessons/5/reserve/")).isEqualTo("/lessons/:id/reserve");
        assertThat(VisitService.landingPath("/")).isEqualTo("/");
        assertThat(VisitService.landingPath("https://evil.com")).isNull();
    }
}
