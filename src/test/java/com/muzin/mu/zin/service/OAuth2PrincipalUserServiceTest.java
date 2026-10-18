package com.muzin.mu.zin.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 소셜 제공자 응답 -> 이메일 인증 여부 판단
class OAuth2PrincipalUserServiceTest {

    @Test
    void google_email_verified_true면_인증() {
        Map<String, Object> a = OAuth2PrincipalUserService.standardize("google",
                Map.of("sub", "123", "email", "a@gmail.com", "email_verified", true));
        assertThat(a.get("email")).isEqualTo("a@gmail.com");
        assertThat(a.get("emailVerified")).isEqualTo(true);
    }

    @Test
    void google_email_verified_false면_미인증() {
        Map<String, Object> a = OAuth2PrincipalUserService.standardize("google",
                Map.of("sub", "123", "email", "a@example.com", "email_verified", false));
        assertThat(a.get("emailVerified")).isEqualTo(false);
    }

    @Test
    void kakao_valid와_verified_둘다_true일때만_인증() {
        Map<String, Object> both = OAuth2PrincipalUserService.standardize("kakao", Map.of("id", 1L,
                "kakao_account", Map.of("email", "k@kakao.com", "is_email_valid", true, "is_email_verified", true)));
        Map<String, Object> notValid = OAuth2PrincipalUserService.standardize("kakao", Map.of("id", 1L,
                "kakao_account", Map.of("email", "k@kakao.com", "is_email_valid", false, "is_email_verified", true)));
        assertThat(both.get("providerUserId")).isEqualTo("1");
        assertThat(both.get("emailVerified")).isEqualTo(true);
        assertThat(notValid.get("emailVerified")).isEqualTo(false);
    }

    @Test
    void naver는_인증_여부를_주지_않아_미인증() {
        Map<String, Object> a = OAuth2PrincipalUserService.standardize("naver",
                Map.of("response", Map.of("id", "n1", "email", "n@naver.com")));
        assertThat(a.get("email")).isEqualTo("n@naver.com");
        assertThat(a.get("emailVerified")).isEqualTo(false);
    }

    @Test
    void 이메일_미동의여도_오류없이_email_null() {
        Map<String, Object> kakaoAccount = new HashMap<>();
        kakaoAccount.put("email_needs_agreement", true);
        Map<String, Object> a = OAuth2PrincipalUserService.standardize("kakao",
                Map.of("id", 2L, "kakao_account", kakaoAccount));
        assertThat(a.get("email")).isNull();
        assertThat(a.get("emailVerified")).isEqualTo(false);
    }
}
