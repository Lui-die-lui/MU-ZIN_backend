package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.auth.UpdateProfileImageRequest;
import com.muzin.mu.zin.dto.auth.UpdateUsernameRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.lesson.UpdateUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UpdateUserController {

    private final UpdateUserService updateUserService;

    // 유저명 변경
    @PatchMapping("/username")
    public ResponseEntity<ApiRespDto<?>> updateUsername(
            @AuthenticationPrincipal PrincipalUser principalUser,
            @RequestBody UpdateUsernameRequest req
    ) {
        updateUserService.updateUsername(principalUser.getUserId(), req.username());
        return ResponseEntity.ok(new ApiRespDto<>("success", "이름이 수정되었습니다.", null));
    }

    // 프로필 이미지 변경
    @PatchMapping("/profile-image")
    public ResponseEntity<ApiRespDto<?>> updateProfileImage(
            @AuthenticationPrincipal PrincipalUser principalUser,
            @RequestBody UpdateProfileImageRequest req
            ) {
        updateUserService.updateProfileImage(principalUser.getUserId(), req.profileImgUrl());
        return ResponseEntity.ok(new ApiRespDto<>("success", "프로필 이미지가 수정되었습니다.", null));
    }

}
