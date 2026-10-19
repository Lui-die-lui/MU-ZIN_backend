package com.muzin.mu.zin.ai.provider;

import lombok.Getter;

// 제공자 호출 실패
// billing: 이 호출이 청구됐는지
//  - NOT_BILLED: 요청이 처리되지 않음이 확실 (연결 실패, 4xx 거절)
//  - UNCERTAIN: 요청이 보내졌지만 결과를 모름 (응답 대기 시간 초과, 5xx, 응답을 읽지 못함) -> 예약액으로 보수 정산
//  - KNOWN: 응답은 받았고 usage 도 있음 (사용량은 정확, 내용만 쓸 수 없음)
@Getter
public class AiProviderException extends RuntimeException {

    public enum Billing { NOT_BILLED, UNCERTAIN, KNOWN }

    private final Billing billing;
    private final AiProvider.Usage usage; // KNOWN 일 때 이 호출의 사용량

    public AiProviderException(Billing billing, String message) {
        this(billing, message, null, null);
    }

    public AiProviderException(Billing billing, String message, AiProvider.Usage usage, Throwable cause) {
        super(message, cause);
        this.billing = billing;
        this.usage = usage == null ? AiProvider.Usage.ZERO : usage;
    }
}
