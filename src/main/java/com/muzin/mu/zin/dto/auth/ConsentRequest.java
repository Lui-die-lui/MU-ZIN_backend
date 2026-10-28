package com.muzin.mu.zin.dto.auth;

// 가입 동의 세 가지 (모두 true 여야 함). null = 항목을 보내지 않음 (배포 전 화면)
public record ConsentRequest(
        Boolean agreeTerms,   // 이용약관
        Boolean agreePrivacy, // 개인정보 수집·이용
        Boolean ageOver14     // 만 14세 이상
) {
}
