package com.muzin.mu.zin.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하로 입력해주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해주세요.")
        String password,

        @NotBlank(message = "비밀번호 확인을 입력해주세요.")
        String passwordConfirm,

        // 가입 동의 (이용약관 · 개인정보 수집·이용 · 만 14세 이상). 검사는 ConsentService.checkSignup
        Boolean agreeTerms,
        Boolean agreePrivacy,
        Boolean ageOver14
) {
    public ConsentRequest consent() {
        return new ConsentRequest(agreeTerms, agreePrivacy, ageOver14);
    }
}
