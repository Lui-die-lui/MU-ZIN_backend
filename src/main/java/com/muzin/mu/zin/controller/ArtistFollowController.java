package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.artist.ArtistFollowResponse;
import com.muzin.mu.zin.dto.artist.ArtistSearchResponse;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.ArtistSearch.ArtistFollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 아티스트 팔로우 (즐겨찾기)
// GET /artists/{id}/follow 는 공개 (SecurityConfig 의 GET /artists/**), 나머지는 로그인 필요
@RestController
@RequiredArgsConstructor
public class ArtistFollowController {

    private final ArtistFollowService followService;

    // 팔로워 수 + 내가 팔로우 중인지 (비로그인이면 false)
    @GetMapping("/artists/{artistProfileId}/follow")
    public ApiRespDto<ArtistFollowResponse> getFollowStatus(
            @PathVariable Long artistProfileId,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        Long viewer = principalUser == null ? null : principalUser.getUserId();
        return ApiRespDto.ok(followService.getStatus(artistProfileId, viewer));
    }

    @PutMapping("/artists/{artistProfileId}/follow")
    public ApiRespDto<ArtistFollowResponse> follow(
            @PathVariable Long artistProfileId,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        return ApiRespDto.ok(followService.follow(artistProfileId, principalUser.getUserId()));
    }

    @DeleteMapping("/artists/{artistProfileId}/follow")
    public ApiRespDto<ArtistFollowResponse> unfollow(
            @PathVariable Long artistProfileId,
            @AuthenticationPrincipal PrincipalUser principalUser
    ) {
        return ApiRespDto.ok(followService.unfollow(artistProfileId, principalUser.getUserId()));
    }

    // 내가 팔로우한 아티스트 (나만 볼 수 있음)
    @GetMapping("/follows/me")
    public ApiRespDto<List<ArtistSearchResponse>> getMyFollowings(@AuthenticationPrincipal PrincipalUser principalUser) {
        return ApiRespDto.ok(followService.getMyFollowings(principalUser.getUserId()));
    }
}
