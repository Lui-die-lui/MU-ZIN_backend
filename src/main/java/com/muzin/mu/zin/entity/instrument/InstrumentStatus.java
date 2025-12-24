package com.muzin.mu.zin.entity.instrument;

public enum InstrumentStatus {
    APPROVED,   // 승인됨 (노출 OK)
    PENDING,    // 검수 대기 (노출 X)
    REJECTED,   // 반려 (노출 X)
    INACTIVE    // 비활성 (노출 X) - 승인된 악기를 내릴 때
}
