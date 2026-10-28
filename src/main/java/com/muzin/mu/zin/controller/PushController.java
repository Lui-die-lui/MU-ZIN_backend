package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.push.PushConfigResponse;
import com.muzin.mu.zin.dto.push.PushEndpointRequest;
import com.muzin.mu.zin.dto.push.PushSubscribeRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.push.PushService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// 웹 푸시 구독 (모두 로그인 필요 - 구독은 로그인한 계정에 연결)
@RestController
@RequiredArgsConstructor
@RequestMapping("/push")
public class PushController {

    private final PushService pushService;

    // 구독에 쓸 서버 공개키
    @GetMapping("/config")
    public ApiRespDto<PushConfigResponse> config() {
        return ApiRespDto.ok(new PushConfigResponse(pushService.enabled(), pushService.publicKey()));
    }

    // 등록·갱신 (같은 브라우저 endpoint 면 덮어씀 -> 다른 계정으로 로그인해 등록하면 이 계정으로 옮겨짐)
    @PutMapping("/subscriptions")
    public ApiRespDto<Void> subscribe(
            @Valid @RequestBody PushSubscribeRequest req,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        pushService.subscribe(principalUser.getUserId(), req.endpoint(), req.keys().p256dh(), req.keys().auth(), userAgent);
        return ApiRespDto.ok("이 기기에서 알림을 받아요.", null);
    }

    // 해제 (내 구독만)
    @PostMapping("/subscriptions/delete")
    public ApiRespDto<Void> unsubscribe(
            @Valid @RequestBody PushEndpointRequest req,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        pushService.unsubscribe(principalUser.getUserId(), req.endpoint());
        return ApiRespDto.ok("이 기기에서 알림을 받지 않아요.", null);
    }

    // 이 브라우저 구독이 지금 계정에 연결돼 있는지
    @PostMapping("/subscriptions/status")
    public ApiRespDto<Map<String, Boolean>> status(
            @Valid @RequestBody PushEndpointRequest req,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        return ApiRespDto.ok(Map.of("subscribed", pushService.isSubscribed(principalUser.getUserId(), req.endpoint())));
    }
}
