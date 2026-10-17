package com.muzin.mu.zin.dto;

// 공통 응답 DTO
// 성공: HTTP 2xx + status "success" / 실패: HTTP 4xx·5xx + status "failed" (GlobalExceptionHandler)
public record ApiRespDto<T> (
     String status,
     String message,
     T data
) {
    public static <T> ApiRespDto<T> ok(T data) {
        return new ApiRespDto<>("success", null, data);
    }

    public static <T> ApiRespDto<T> ok(String message, T data) {
        return new ApiRespDto<>("success", message, data);
    }
}
