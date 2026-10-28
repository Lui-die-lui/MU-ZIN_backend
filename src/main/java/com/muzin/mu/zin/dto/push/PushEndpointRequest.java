package com.muzin.mu.zin.dto.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 구독 해제 · 상태 확인용
public record PushEndpointRequest(@NotBlank @Size(max = 1024) String endpoint) {
}
