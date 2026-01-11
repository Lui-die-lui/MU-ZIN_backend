package com.muzin.mu.zin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// 중복 응답용 DTO
public record ApiRespDto<T> (
     String status,
     String message,
     T data
) {
    public static <T> ApiRespDto<T> ok(T data) {
        return new ApiRespDto<>("ok", null, data);
    }

    public static <T> ApiRespDto<T> fail(String message) {
        return new ApiRespDto<>("failed", message, null);
    }
}
