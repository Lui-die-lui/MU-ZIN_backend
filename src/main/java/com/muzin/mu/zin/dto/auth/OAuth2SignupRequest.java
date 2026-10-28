package com.muzin.mu.zin.dto.auth;

import jakarta.validation.constraints.NotBlank;

// 소셜 첫 가입 완료: 소셜 로그인 직후 받은 임시 가입 토큰 + 동의
public record OAuth2SignupRequest(
        @NotBlank(message = "가입 정보가 없어요. 소셜 로그인을 다시 시도해 주세요.")
        String signupToken,
        Boolean agreeTerms,
        Boolean agreePrivacy,
        Boolean ageOver14
) {
    public ConsentRequest consent() {
        return new ConsentRequest(agreeTerms, agreePrivacy, ageOver14);
    }
}
