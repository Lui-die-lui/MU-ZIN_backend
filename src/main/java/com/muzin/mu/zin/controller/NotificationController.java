package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.notification.NotificationResponse;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/me")
    public ApiRespDto<List<NotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal PrincipalUser principalUser
            ) {
        return notificationService.getMyNotifications(principalUser);
    }

    @GetMapping("/me/unread-count")
    public ApiRespDto<Long> getMyUnreadCount(
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        return notificationService.getMyUnreadCount(principalUser);
    }

    @PatchMapping("/me/{notificationId}/read")
    public ApiRespDto<?> markRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        return notificationService.markRead(notificationId, principalUser);
    }

    @PatchMapping("/me/read-all")
    public ApiRespDto<?> markAllRead(
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        return notificationService.markAllRead(principalUser);
    }
}
