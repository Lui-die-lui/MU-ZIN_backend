package com.muzin.mu.zin.dto.push;

// enabled=false 면 서버에 푸시 키가 없음 (알림 켜기 버튼을 막음)
public record PushConfigResponse(boolean enabled, String publicKey) {
}
