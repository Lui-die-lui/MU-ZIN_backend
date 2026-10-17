package com.muzin.mu.zin.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

// 실패 응답의 data 부분: 프론트가 분기할 수 있는 에러 코드
// + (검증 실패 시) 필드별 메시지, (횟수 제한 시) 다시 시도 가능까지 남은 초
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDetail(
        String code,
        Map<String, String> fieldErrors,
        Long retryAfterSeconds
) {
    public static ErrorDetail of(String code) {
        return new ErrorDetail(code, null, null);
    }
}
