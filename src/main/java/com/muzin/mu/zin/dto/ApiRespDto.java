package com.muzin.mu.zin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// 중복 응답용 DTO
@Data
@AllArgsConstructor
public class ApiRespDto<T> {
    private String status;
    private String message;
    private T data;
}
