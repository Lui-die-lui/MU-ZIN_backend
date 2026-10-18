package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.chat.InquiryReplyRequest;
import com.muzin.mu.zin.dto.chat.InquiryRequest;
import com.muzin.mu.zin.dto.chat.InquiryResponse;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.chat.InquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 레슨 문의 (로그인 필요)
@RestController
@RequiredArgsConstructor
@RequestMapping("/inquiries")
public class InquiryController {

    private final InquiryService inquiryService;

    @PostMapping
    public ApiRespDto<InquiryResponse> create(@AuthenticationPrincipal PrincipalUser p, @Valid @RequestBody InquiryRequest req) {
        return ApiRespDto.ok("문의를 보냈어요.", inquiryService.create(p.getUserId(), req.artistProfileId(), req.content()));
    }

    // 내가 보낸 문의
    @GetMapping("/sent")
    public ApiRespDto<List<InquiryResponse>> sent(@AuthenticationPrincipal PrincipalUser p) {
        return ApiRespDto.ok(inquiryService.getSent(p.getUserId()));
    }

    // 내가(아티스트) 받은 문의
    @GetMapping("/received")
    public ApiRespDto<List<InquiryResponse>> received(@AuthenticationPrincipal PrincipalUser p) {
        return ApiRespDto.ok(inquiryService.getReceived(p.getUserId()));
    }

    @GetMapping("/received/open-count")
    public ApiRespDto<Map<String, Long>> openCount(@AuthenticationPrincipal PrincipalUser p) {
        return ApiRespDto.ok(Map.of("openCount", inquiryService.countOpenReceived(p.getUserId())));
    }

    @PostMapping("/{inquiryId}/reply")
    public ApiRespDto<InquiryResponse> reply(@AuthenticationPrincipal PrincipalUser p, @PathVariable Long inquiryId,
                                             @Valid @RequestBody InquiryReplyRequest req) {
        return ApiRespDto.ok("답변했어요.", inquiryService.reply(p.getUserId(), inquiryId, req.content()));
    }

    @PostMapping("/{inquiryId}/to-chat")
    public ApiRespDto<InquiryResponse> toChat(@AuthenticationPrincipal PrincipalUser p, @PathVariable Long inquiryId) {
        return ApiRespDto.ok("채팅으로 넘겼어요.", inquiryService.toChat(p.getUserId(), inquiryId));
    }
}
