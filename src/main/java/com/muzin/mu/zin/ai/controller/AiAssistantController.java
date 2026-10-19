package com.muzin.mu.zin.ai.controller;

import com.muzin.mu.zin.ai.dto.AiAssistantDtos.*;
import com.muzin.mu.zin.ai.service.AiAssistantService;
import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.security.model.PrincipalUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// AI 레슨 내비게이터 (로그인 필요 - SecurityConfig 의 anyRequest().authenticated())
// 같은 요청을 다시 보내도(재시도·중복 클릭) 한 번만 처리되도록 시작·질문 추가는 Idempotency-Key 헤더 필수
@RestController
@RequestMapping("/ai/lesson-assistant")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService service;

    @GetMapping("/quota")
    public ApiRespDto<QuotaResponse> quota(@AuthenticationPrincipal PrincipalUser principal) {
        return ApiRespDto.ok(service.quota(principal.getUserId()));
    }

    @GetMapping("/conversations")
    public ApiRespDto<List<ConversationSummary>> list(@AuthenticationPrincipal PrincipalUser principal) {
        return ApiRespDto.ok(service.list(principal.getUserId()));
    }

    @PostMapping("/conversations")
    public ApiRespDto<ConversationDetail> start(@AuthenticationPrincipal PrincipalUser principal,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
                                                @Valid @RequestBody StartRequest req) {
        return ApiRespDto.ok(service.start(principal.getUserId(), idemKey, req));
    }

    @GetMapping("/conversations/{conversationId}")
    public ApiRespDto<ConversationDetail> detail(@AuthenticationPrincipal PrincipalUser principal, @PathVariable long conversationId) {
        return ApiRespDto.ok(service.detail(principal.getUserId(), conversationId));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ApiRespDto<ConversationDetail> send(@AuthenticationPrincipal PrincipalUser principal, @PathVariable long conversationId,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
                                               @Valid @RequestBody MessageRequest req) {
        return ApiRespDto.ok(service.send(principal.getUserId(), conversationId, idemKey, req));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ApiRespDto<Void> delete(@AuthenticationPrincipal PrincipalUser principal, @PathVariable long conversationId) {
        service.delete(principal.getUserId(), conversationId);
        return ApiRespDto.ok("대화를 삭제했어요.", null);
    }

    // 내 대화 기록 전체 삭제 (처리 중인 대화는 남음)
    @DeleteMapping("/conversations")
    public ApiRespDto<Map<String, Integer>> deleteAll(@AuthenticationPrincipal PrincipalUser principal) {
        return ApiRespDto.ok("대화 기록을 삭제했어요.", Map.of("deleted", service.deleteAll(principal.getUserId())));
    }
}
