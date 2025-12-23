package com.muzin.mu.zin.service.artistProfile;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.artist.ArtistProfileResponse;
import com.muzin.mu.zin.dto.artist.ArtistProfileUpsertRequest;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.ArtistStatus;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.repository.ArtistProfileRepository;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.security.model.PrincipalUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistApplicationServiceImpl implements ArtistProfileService{

    private final UserRepository userRepository;
    private final ArtistProfileRepository artistProfileRepository;


    @Override
    public ApiRespDto<ArtistProfileResponse> getMyArtistProfile(PrincipalUser principalUser) {
        ArtistProfile profile = getProfileOrThrow(principalUser.getUserId());
        return new ApiRespDto<>("success", "", toResponse(profile));
    }

    /*
    * NONE 만 가능 (임시저장/작성)
    * 프로필이 없으면 생성
    * 있으면 업데이트
    * */

    @Override
    @Transactional
    public ApiRespDto<ArtistProfileResponse> saveDraftProfile(ArtistProfileUpsertRequest req, PrincipalUser principalUser) {
       User user = getUserOrThrow(principalUser.getUserId());
       requireStatus(user, EnumSet.of(ArtistStatus.NONE));

       ArtistProfile profile = artistProfileRepository.findByUser_UserId(user.getUserId())
               .orElseGet(() -> ArtistProfile.builder()
                       .user(user)
                       .build());

       profile.updateProfile(req.bio(), req.career(), req.majorName());
       ArtistProfile saved = artistProfileRepository.save(profile);

       return new ApiRespDto<>("success", "임시 저장 완료", toResponse(saved));
    }

    /*
    * NONE 만 가능
    * - 프로필 등록 시 필수값 검증
    * - user.artistStatus = PENDING 으로 전환됨
    * */
    @Override
    @Transactional
    public ApiRespDto<?> submitArtistApplication(PrincipalUser principalUser) {
        User user = getUserOrThrow(principalUser.getUserId());
        requireStatus(user, EnumSet.of(ArtistStatus.NONE));

        ArtistProfile profile = getProfileOrThrow(user.getUserId());
        validateForSubmit(profile);

        // 제출 시 PENDING으로 전환시켜줌
        user.setArtistStatus(ArtistStatus.PENDING);
        userRepository.save(user);

        return new ApiRespDto<>("success", "아티스트 전환 신청이 제출되었습니다.", null);
    }

    /*
    * APPROVED 상태에서만 가능(승인 후 자유 수정)
    * */
    @Override
    @Transactional
    public ApiRespDto<ArtistProfileResponse> updateApprovedArtistProfile(ArtistProfileUpsertRequest req, PrincipalUser principalUser) {
        User user = getUserOrThrow(principalUser.getUserId());
        requireStatus(user, EnumSet.of(ArtistStatus.APPROVED));

        ArtistProfile profile = getProfileOrThrow(user.getUserId());
        profile.updateProfile(req.bio(), req.career(), req.majorName()); // 전공을 수정 가능...? 조금 생각해봐야할듯

        ArtistProfile saved = artistProfileRepository.save(profile);
        return new ApiRespDto<>("success", "프로필 수정 완료", toResponse(saved));
    }


    // guard & helpers

    // 요청 보내는 user의 Status를 확인
    private void requireStatus(User user, EnumSet<ArtistStatus> allowed) {
        if (!allowed.contains(user.getArtistStatus())) {
            // PENDING, / REJECTED 포함 여기서 전부 막힘
            throw new IllegalStateException("현재 상태에서는 불가능한 요청입니다. : " + user.getArtistStatus());
        }
    }

    // 제출된 프로필에 빠진 내용들을 확인시켜줌
    private void validateForSubmit(ArtistProfile profile) {
        if (profile.getBio() == null || profile.getBio().isBlank()) {
            throw new IllegalArgumentException("자기 소개는 필수입니다.");
        }
        if (profile.getMajorName() == null || profile.getMajorName().isBlank()) {
            throw new IllegalArgumentException("전공 입력은 필수입니다.");
        }
        if (profile.getCareer() == null || profile.getCareer().isBlank()) {
            throw new IllegalArgumentException("경력 입력은 필수입니다.");
        }
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다. : " + userId));
    }

    private ArtistProfile getProfileOrThrow(Long userId) {
        return artistProfileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("아티스트 전환 프로필이 없습니다. 먼저 작성하세요."));
    }

    private ArtistProfileResponse toResponse(ArtistProfile profile) {
        return new ArtistProfileResponse(
                profile.getArtistProfileId(),
                profile.getUser().getUserId(),
                profile.getBio(),
                profile.getCareer(),
                profile.getMajorName(),
                java.util.List.of() // instruments는 다음 단계에서 매핑
                // 악기 목록을 아직 구현 안 해서 지금은 비워서 내려줌 - 불변 빈 리스트 생성
        );
    }

}
