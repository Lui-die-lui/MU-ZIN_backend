package com.muzin.mu.zin.dto.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 브라우저 PushSubscription.toJSON() 모양 그대로 { endpoint, keys: { p256dh, auth } }
// 누구의 구독인지는 로그인 정보로 정함 (userId 를 받지 않음)
public record PushSubscribeRequest(
        @NotBlank @Size(max = 1024) String endpoint,
        @NotNull @Valid Keys keys
) {
    public record Keys(
            @NotBlank @Size(max = 120) String p256dh,
            @NotBlank @Size(max = 40) String auth
    ) { }
}
