package com.muzin.mu.zin.ai.provider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

// AI 제공자 (OpenAI Responses API 형식). 구현: OpenAiResponsesProvider(실제) / StubAiProvider(가짜, 기본)
// 스트리밍은 쓰지 않음 - 완성된 응답 한 번 (비용 정산·실패 복구를 먼저 검증한 뒤 따로 추가)
public interface AiProvider {

    String name();

    Response respond(Request request);

    // toolChoice: null 이면 "auto", 함수 이름이면 그 함수를 반드시 호출
    record Request(String model, String instructions, ArrayNode input, ArrayNode tools, String toolChoice, int maxOutputTokens) { }

    record FunctionCall(String callId, String name, String arguments) { }

    record Usage(long inputTokens, long cachedInputTokens, long outputTokens) {
        public static final Usage ZERO = new Usage(0, 0, 0);

        public Usage plus(Usage o) {
            return new Usage(inputTokens + o.inputTokens, cachedInputTokens + o.cachedInputTokens, outputTokens + o.outputTokens);
        }
    }

    // outputItems: 응답 output 배열 그대로 (도구 호출 뒤 다음 요청 input 에 이어 붙임)
    record Response(String status, List<JsonNode> outputItems, List<FunctionCall> functionCalls, String text, Usage usage) { }
}
