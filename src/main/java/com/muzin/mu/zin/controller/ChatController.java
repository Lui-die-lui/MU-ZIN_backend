package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.chat.ChatMessageResponse;
import com.muzin.mu.zin.dto.chat.ChatReadRequest;
import com.muzin.mu.zin.dto.chat.ChatRoomResponse;
import com.muzin.mu.zin.dto.chat.ChatSendRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.chat.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 수강생 <-> 아티스트 1:1 채팅 (로그인 필요, 참여자만)
@RestController
@RequiredArgsConstructor
@RequestMapping("/chats")
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public ApiRespDto<List<ChatRoomResponse>> myRooms(@AuthenticationPrincipal PrincipalUser p) {
        return ApiRespDto.ok(chatService.getMyRooms(p.getUserId()));
    }

    @GetMapping("/unread-count")
    public ApiRespDto<Map<String, Long>> unreadCount(@AuthenticationPrincipal PrincipalUser p) {
        return ApiRespDto.ok(Map.of("unreadCount", chatService.getUnreadTotal(p.getUserId())));
    }

    // 예약 상세 -> 채팅하기
    @GetMapping("/by-reservation/{reservationId}")
    public ApiRespDto<Map<String, Long>> roomForReservation(@AuthenticationPrincipal PrincipalUser p, @PathVariable Long reservationId) {
        return ApiRespDto.ok(Map.of("chatRoomId", chatService.findRoomIdForReservation(p.getUserId(), reservationId)));
    }

    @GetMapping("/{roomId}")
    public ApiRespDto<ChatRoomResponse> room(@AuthenticationPrincipal PrincipalUser p, @PathVariable Long roomId) {
        return ApiRespDto.ok(chatService.getRoom(p.getUserId(), roomId));
    }

    // 최신 30개, beforeId 를 주면 그 이전 30개
    @GetMapping("/{roomId}/messages")
    public ApiRespDto<List<ChatMessageResponse>> messages(@AuthenticationPrincipal PrincipalUser p, @PathVariable Long roomId,
                                                         @RequestParam(required = false) Long beforeId) {
        return ApiRespDto.ok(chatService.getMessages(p.getUserId(), roomId, beforeId));
    }

    @PostMapping("/{roomId}/messages")
    public ApiRespDto<ChatMessageResponse> send(@AuthenticationPrincipal PrincipalUser p, @PathVariable Long roomId,
                                                @Valid @RequestBody ChatSendRequest req) {
        return ApiRespDto.ok(chatService.send(p.getUserId(), roomId, req.content()));
    }

    // 내 메시지 수정 (보낸 지 24시간 안)
    @PatchMapping("/{roomId}/messages/{messageId}")
    public ApiRespDto<ChatMessageResponse> editMessage(@AuthenticationPrincipal PrincipalUser p, @PathVariable Long roomId,
                                                       @PathVariable Long messageId, @Valid @RequestBody com.muzin.mu.zin.dto.chat.ChatEditRequest req) {
        return ApiRespDto.ok(chatService.editMessage(p.getUserId(), roomId, messageId, req.content()));
    }

    // 내 메시지 삭제 (언제든, 상대 화면에서도 "삭제된 메시지예요")
    @DeleteMapping("/{roomId}/messages/{messageId}")
    public ApiRespDto<ChatMessageResponse> deleteMessage(@AuthenticationPrincipal PrincipalUser p, @PathVariable Long roomId, @PathVariable Long messageId) {
        return ApiRespDto.ok(chatService.deleteMessage(p.getUserId(), roomId, messageId));
    }

    // 나가기: 내 화면에서 메시지가 지워지고 복원되지 않음 (둘 다 나가면 방 삭제)
    @PostMapping("/{roomId}/leave")
    public ApiRespDto<Void> leave(@AuthenticationPrincipal PrincipalUser p, @PathVariable Long roomId) {
        chatService.leave(p.getUserId(), roomId);
        return ApiRespDto.ok("대화방을 나갔어요.", null);
    }

    @PostMapping("/{roomId}/read")
    public ApiRespDto<Void> read(@AuthenticationPrincipal PrincipalUser p, @PathVariable Long roomId, @RequestBody(required = false) ChatReadRequest req) {
        chatService.markRead(p.getUserId(), roomId, req == null ? null : req.lastMessageId());
        return ApiRespDto.ok(null);
    }
}
