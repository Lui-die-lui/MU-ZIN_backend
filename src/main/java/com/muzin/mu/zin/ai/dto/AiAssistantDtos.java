package com.muzin.mu.zin.ai.dto;

import com.muzin.mu.zin.ai.tools.AiToolService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// AI 레슨 내비게이터 API 요청·응답
public final class AiAssistantDtos {
    private AiAssistantDtos() {}

    public static final int MAX_MESSAGE_CHARS = 500;

    // styleContext: 사용자가 취향 반영을 선택했을 때만 (muzin.style-finder.context/v2), 아니면 null
    public record StartRequest(
            @NotBlank(message = "질문을 입력해주세요.")
            @Size(max = MAX_MESSAGE_CHARS, message = "질문은 500자까지 보낼 수 있어요.")
            String message,
            JsonNode styleContext
    ) { }

    public record MessageRequest(
            @NotBlank(message = "질문을 입력해주세요.")
            @Size(max = MAX_MESSAGE_CHARS, message = "질문은 500자까지 보낼 수 있어요.")
            String message
    ) { }

    // available=false 면 unavailableCode(AI_BUDGET_EXHAUSTED / AI_UNAVAILABLE) 로 이유 구분
    public record QuotaResponse(int weeklyLimit, int used, int remaining, LocalDate weekStart, LocalDateTime resetsAt,
                                int maxUserMessages, boolean available, String unavailableCode, String unavailableMessage) { }

    public record StyleContextSummary(List<String> recommended, String region, List<String> keywords) { }

    public record MessageView(long messageId, String role, String content, String followUpQuestion,
                              List<AiToolService.Card> recommendations, boolean pending, LocalDateTime createDt) { }

    public record ConversationDetail(long conversationId, String title, String status, StyleContextSummary styleContext,
                                     int userMessageCount, int maxUserMessages, int remainingQuestions, boolean inFlight,
                                     LocalDateTime createDt, LocalDateTime updateDt, List<MessageView> messages) { }

    public record ConversationSummary(long conversationId, String title, String status, int userMessageCount,
                                      int maxUserMessages, LocalDateTime createDt, LocalDateTime updateDt) { }
}
