package com.muzin.mu.zin.service.artistProfile;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.artist.ArtistProfileResponse;
import com.muzin.mu.zin.dto.artist.ArtistProfileUpsertRequest;
import com.muzin.mu.zin.security.model.PrincipalUser;

// interface 인 이유
// 구현 분리
// ArtistApplicationServiceImpl, ArtistApplicationAdminServiceImpl처럼 구현을 갈라야 할 때 깔끔
public interface ArtistProfileService {

    // NONE 상태인 user만 신청 가능(임시 저장 및 작성)
    ApiRespDto<ArtistProfileResponse> saveDraftProfile(ArtistProfileUpsertRequest req, PrincipalUser principalUser);

    // NONE 상태인 user만 신청 가능(제출 - 필수 값 검증 후 PENDING으로 변경)
    ApiRespDto<?> submitArtistApplication(PrincipalUser principalUser);

    // APPROVED만 가능 - 승인 후 프로필 수정
    ApiRespDto<ArtistProfileResponse> updateApprovedArtistProfile(ArtistProfileUpsertRequest req, PrincipalUser principalUser);

    // 내 프로필 조회(상태 무관 - 조회만)
    ApiRespDto<ArtistProfileResponse> getMyArtistProfile(PrincipalUser principalUser);

}
