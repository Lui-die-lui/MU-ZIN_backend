package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.artist.ArtistInstrumentAddRequest;
import com.muzin.mu.zin.dto.artist.ArtistProfileResponse;
import com.muzin.mu.zin.dto.artist.ArtistProfileUpsertRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.artistProfile.ArtistProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/artist/profile")
public class ArtistController {

    private final ArtistProfileService artistProfileService;

    // draft 저장 (NONE 상태에서만 가능)
    @PutMapping("/me/draft")
    public ApiRespDto<ArtistProfileResponse> saveDraft(
            @RequestBody ArtistProfileUpsertRequest req,
            @AuthenticationPrincipal PrincipalUser principalUser
            ) {
        return artistProfileService.saveDraftProfile(req, principalUser);
    }

    // 신청 제출 (NONE -> PENDING)
    @PostMapping("/me/submit")
    public ApiRespDto<?> submit(@AuthenticationPrincipal PrincipalUser principalUser) {
        return artistProfileService.submitArtistApplication(principalUser);
    }

    // 승인 후 프로필 수정 (APPROVED만 가능)
    @PutMapping("/me")
    public ApiRespDto<ArtistProfileResponse> updateApprovedProfile(
            @RequestBody ArtistProfileUpsertRequest req,
            @AuthenticationPrincipal PrincipalUser principalUser
    ){
        return artistProfileService.updateApprovedArtistProfile(req, principalUser);
    }

    // 전환 신청된 정보를 해당 유저가 확인하는 기능
    @GetMapping("/me")
    public ApiRespDto<ArtistProfileResponse> getMyProfile(@AuthenticationPrincipal PrincipalUser principalUser) {
        return artistProfileService.getMyArtistProfile(principalUser);
    }

    // 악기 등록 및 교체
    @PutMapping("/me/instruments")
    public ApiRespDto<ArtistProfileResponse> setMyInstruments(
            @RequestBody ArtistInstrumentAddRequest req,
            @AuthenticationPrincipal PrincipalUser principalUser
            ) {
        return artistProfileService.setMyInstruments(req.instrumentIds(), principalUser);
    }
}
