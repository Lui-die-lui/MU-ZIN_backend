package com.muzin.mu.zin.dto.reservation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReservationCreateRequest(
        @NotNull(message = "예약할 시간을 선택해주세요.")
        Long timeSlotId,

        @Size(max = 1000, message = "요청 메시지는 1000자 이하로 입력해주세요.")
        String requestMsg,

        // "레슨은 아티스트와 직접 하는 약속, 결제·환불은 두 분이 직접" 안내 확인 (true 면 확인 시각 저장)
        Boolean noticeAcknowledged
) {
}
