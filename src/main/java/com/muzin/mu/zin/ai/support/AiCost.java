package com.muzin.mu.zin.ai.support;

import com.muzin.mu.zin.ai.config.AiProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 비용 계산 (마이크로 USD 정수)
// 단가가 "USD / 100만 토큰" 이므로 토큰 1개 비용(마이크로 USD) = 단가 숫자 그대로 -> tokens x 단가, 올림
public final class AiCost {
    private AiCost() {}

    public static final long MICROS_PER_USD = 1_000_000L;

    public static long usdToMicros(BigDecimal usd) {
        return usd.multiply(BigDecimal.valueOf(MICROS_PER_USD)).setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    // 실제 사용량 비용. inputTokens 는 캐시된 토큰을 포함한 전체 입력 (OpenAI usage.input_tokens 와 같은 의미)
    public static long actualMicros(AiProperties.ModelPrice price, long inputTokens, long cachedInputTokens, long outputTokens) {
        long cached = Math.max(0, Math.min(cachedInputTokens, inputTokens));
        BigDecimal cachedPrice = price.getCachedInputUsdPerMtok() != null ? price.getCachedInputUsdPerMtok() : price.getInputUsdPerMtok();
        BigDecimal total = price.getInputUsdPerMtok().multiply(BigDecimal.valueOf(inputTokens - cached))
                .add(cachedPrice.multiply(BigDecimal.valueOf(cached)))
                .add(price.getOutputUsdPerMtok().multiply(BigDecimal.valueOf(Math.max(0, outputTokens))));
        return total.setScale(0, RoundingMode.CEILING).longValueExact();
    }

    // 예상 최대 비용: 캐시 할인 없이 최대 입력·출력 토큰으로
    public static long maxMicros(AiProperties.ModelPrice price, long maxInputTokens, long maxOutputTokens) {
        return actualMicros(price, maxInputTokens, 0, maxOutputTokens);
    }

    // 입력 글자 수 -> 토큰 상한 추정 (보수적으로 글자 1개 = 토큰 1개, 한글·JSON 모두 실제보다 크게 잡힘)
    public static long estimateTokensUpperBound(long chars) {
        return Math.max(1, chars);
    }
}
