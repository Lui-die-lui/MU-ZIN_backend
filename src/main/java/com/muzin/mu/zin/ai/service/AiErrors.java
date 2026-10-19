package com.muzin.mu.zin.ai.service;

import com.muzin.mu.zin.exception.ApiException;
import org.springframework.http.HttpStatus;

// 클라이언트가 구분하는 에러 코드 (data.code)
public final class AiErrors {
    private AiErrors() {}

    public static final String WEEKLY_LIMIT = "AI_WEEKLY_LIMIT";
    public static final String MESSAGE_LIMIT = "AI_MESSAGE_LIMIT";
    public static final String BUDGET_EXHAUSTED = "AI_BUDGET_EXHAUSTED";
    public static final String UNAVAILABLE = "AI_UNAVAILABLE";
    public static final String AI_ERROR = "AI_ERROR";
    public static final String IN_PROGRESS = "AI_REQUEST_IN_PROGRESS";
    public static final String IDEMPOTENCY_KEY_REQUIRED = "AI_IDEMPOTENCY_KEY_REQUIRED";
    public static final String IDEMPOTENCY_KEY_REUSED = "AI_IDEMPOTENCY_KEY_REUSED";

    public static ApiException weeklyLimit(long retryAfterSeconds) {
        return ApiException.tooManyRequests(WEEKLY_LIMIT, "이번 주 무료 이용 횟수(3회)를 모두 썼어요. 월요일 0시에 다시 3회가 돼요.", retryAfterSeconds);
    }

    public static ApiException messageLimit() {
        return new ApiException(HttpStatus.CONFLICT, MESSAGE_LIMIT, "이 대화에서 할 수 있는 질문(6번)을 모두 썼어요. 새 대화를 시작해 주세요.");
    }

    public static ApiException budgetExhausted() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, BUDGET_EXHAUSTED,
                "이번 달 AI 이용량이 모두 소진됐어요. 레슨 찾기에서 조건으로 직접 찾아보세요. 이용 횟수는 차감되지 않았어요.");
    }

    public static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, "지금은 AI 레슨 내비게이터를 쓸 수 없어요. 레슨 찾기에서 직접 찾아보세요.");
    }

    public static ApiException aiError(boolean firstMessage) {
        return new ApiException(HttpStatus.BAD_GATEWAY, AI_ERROR, firstMessage
                ? "AI 답변을 받지 못했어요. 이용 횟수는 차감되지 않았어요. 잠시 후 다시 시도해 주세요."
                : "AI 답변을 받지 못했어요. 질문 횟수는 차감되지 않았어요. 잠시 후 다시 시도해 주세요.");
    }

    public static ApiException inProgress() {
        return new ApiException(HttpStatus.CONFLICT, IN_PROGRESS, "이전 질문의 답변을 만드는 중이에요. 잠시 후 다시 시도해 주세요.");
    }

    public static ApiException idempotencyKeyRequired() {
        return new ApiException(HttpStatus.BAD_REQUEST, IDEMPOTENCY_KEY_REQUIRED, "요청 식별 값(Idempotency-Key)이 필요해요.");
    }

    public static ApiException idempotencyKeyReused() {
        return new ApiException(HttpStatus.CONFLICT, IDEMPOTENCY_KEY_REUSED, "이미 다른 질문에 쓴 요청 식별 값이에요. 새로 시도해 주세요.");
    }

    public static ApiException notFound() {
        return ApiException.notFound("대화를 찾을 수 없어요."); // 남의 대화도 있는지 알려주지 않음
    }
}
