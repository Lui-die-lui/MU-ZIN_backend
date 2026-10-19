package com.muzin.mu.zin.ai.config;

import com.muzin.mu.zin.ai.provider.AiProvider;
import com.muzin.mu.zin.ai.provider.OpenAiResponsesProvider;
import com.muzin.mu.zin.ai.provider.StubAiProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

// 제공자 선택: AI_PROVIDER=openai 일 때만 실제 OpenAI 를 씀 (기본 stub = 네트워크 호출·과금 없음)
// openai 인데 키나 단가가 비어 있으면 stub 으로 바꾸지 않고 "사용 불가"로 둠 (AiAssistantService.unavailableReason)
@Slf4j
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    public AiProvider aiProvider(AiProperties props, ObjectMapper mapper) {
        if (props.isStub()) {
            log.info("AI 레슨 내비게이터: stub 제공자 (실제 AI 호출 없음)");
            return new StubAiProvider(mapper);
        }
        AiProperties.ModelPrice price = props.getPricing().getModels().get(props.getModel());
        log.info("AI 레슨 내비게이터: OpenAI 제공자, model={}, API 키 {}, 단가 {}", props.getModel(),
                props.getOpenai().getApiKey().isBlank() ? "없음" : "설정됨",
                price != null && price.isComplete() ? "설정됨" : "없음(호출 안 함)");
        return new OpenAiResponsesProvider(props, mapper);
    }
}
