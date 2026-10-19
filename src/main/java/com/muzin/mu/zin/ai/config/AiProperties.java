package com.muzin.mu.zin.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

// AI 레슨 내비게이터 설정 (application.properties 의 app.ai.*)
// 가격은 pricing.models 한곳에서만 관리 (코드에 단가를 두지 않음)
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    public static final String PROVIDER_STUB = "stub";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String STUB_MODEL = "stub-model";

    private String provider = PROVIDER_STUB;
    private String model = "gpt-6-luna";
    private OpenAi openai = new OpenAi();

    private int weeklyConversationLimit = 3;
    private int maxUserMessages = 6; // 첫 질문 1 + 추가 5
    private int retentionDays = 90;
    private boolean includeSampleData = false;

    private int maxOutputTokens = 1500;
    private int maxToolRounds = 4;
    private int maxInputChars = 24000;
    private int maxHistoryMessages = 10;

    private BigDecimal monthlyBudgetUsd = BigDecimal.TEN;

    // 테스트에서 예산 행을 실제 월 행과 분리하려고 앞에 붙이는 값 (운영에서는 비움)
    private String budgetKeyPrefix = "";

    private Pricing pricing = new Pricing();

    public boolean isStub() {
        return !PROVIDER_OPENAI.equalsIgnoreCase(provider);
    }

    // 실제로 쓰는 모델 ID (stub 은 가상 모델)
    public String activeModel() {
        return isStub() ? STUB_MODEL : model;
    }

    // 답변 처리 중 잠금 시간: 한 번의 외부 호출 제한 시간 x 최대 도구 호출 횟수 + 여유
    public long inFlightLockSeconds() {
        return (long) openai.readTimeoutSeconds * (maxToolRounds + 1) + 30;
    }

    @Getter
    @Setter
    public static class OpenAi {
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1";
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 45;
        private boolean store = false;
        private boolean includeReasoningEncryptedContent = true;
    }

    @Getter
    @Setter
    public static class Pricing {
        private Map<String, ModelPrice> models = new HashMap<>();
    }

    // USD / 100만 토큰
    @Getter
    @Setter
    public static class ModelPrice {
        private BigDecimal inputUsdPerMtok;
        private BigDecimal cachedInputUsdPerMtok;
        private BigDecimal outputUsdPerMtok;

        public boolean isComplete() {
            return inputUsdPerMtok != null && outputUsdPerMtok != null;
        }
    }
}
