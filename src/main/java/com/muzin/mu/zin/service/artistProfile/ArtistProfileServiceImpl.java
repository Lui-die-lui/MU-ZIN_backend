package com.muzin.mu.zin.service.artistProfile;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.artist.ArtistProfileResponse;
import com.muzin.mu.zin.dto.artist.ArtistProfileUpsertRequest;
import com.muzin.mu.zin.dto.instrument.InstrumentResponse;
import com.muzin.mu.zin.entity.ArtistInstrument;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.ArtistStatus;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.instrument.Instrument;
import com.muzin.mu.zin.repository.ArtistInstrumentRepository;
import com.muzin.mu.zin.repository.ArtistProfileRepository;
import com.muzin.mu.zin.repository.InstrumentRepository;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.security.model.PrincipalUser;
import com.muzin.mu.zin.service.InstrumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistProfileServiceImpl implements ArtistProfileService{

    private final UserRepository userRepository;
    private final ArtistProfileRepository artistProfileRepository;
    private final ArtistInstrumentRepository artistInstrumentRepository;
    private final InstrumentService instrumentService;


    @Override
    public ApiRespDto<ArtistProfileResponse> getMyArtistProfile(PrincipalUser principalUser) {
        ArtistProfile profile = getProfileOrThrow(principalUser.getUserId());
        return new ApiRespDto<>("success", "조회 완료", toResponse(profile));
//        User user = getUserOrThrow(principalUser.getUserId());
//
//        ArtistProfile profile = artistProfileRepository.findByUser_UserId(user.getUserId())
//                .orElseThrow(() -> new IllegalStateException("아티스트 프로필이 없습니다."));
//
//        return new ApiRespDto<>("success","조회 완료",toResponse(profile));
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
                       .bio(req.bio())
                       .career(req.career())
                       .majorName(req.majorName())
                       .build());

       // 이미 있으면 update
       profile.updateProfile(req.bio(), req.career(), req.majorName());
       ArtistProfile saved = artistProfileRepository.save(profile);

       // 악기 포함 응답(없으면 빈 리스트)
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

    // 악기 목록 교체 (NONE || APPROVED)
    @Transactional
    public ApiRespDto<ArtistProfileResponse> setMyInstruments(List<Long> instrumentIds, PrincipalUser principalUser) {
        User user = getUserOrThrow(principalUser.getUserId());

        // PENDING 상태에서는 변경 불가
//        if (user.getArtistStatus() == ArtistStatus.PENDING) {
//            return new ApiRespDto<>("failed", "심사 중에는 악기 수정이 불가능합니다.",null);
//        }
//
//        // 제출 전이나 심사 완료 후에는 변경 가능
//        if (user.getArtistStatus() != ArtistStatus.NONE && user.getArtistStatus() != ArtistStatus.APPROVED) {
//            return new ApiRespDto<>("failed","현재 상태에서는 악기 수정이 불가능 합니다.", null);
//        }
        requireStatus(user, EnumSet.of(ArtistStatus.NONE, ArtistStatus.APPROVED));

        ArtistProfile profile = artistProfileRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new IllegalStateException("아티스트 프로필이 없습니다."));

        // 중복 제거
        List<Long> uniqueIds = (instrumentIds == null) ? List.of() : instrumentIds.stream().distinct().toList();

        // 승인된 악기만 통과 + 없는 id 검증까지
        List<Instrument> instruments = instrumentService.validateAndGetApprovedByIds(uniqueIds);

        // 기존 매핑 전체 삭제 - 새로 갈아끼움
//        artistInstrumentRepository.deleteByArtistProfile_ArtistProfileId(profile.getArtistProfileId());
//        artistInstrumentRepository.flush(); // 바로 반영
//        artistInstrumentRepository.deleteAllByProfileId(profile.getArtistProfileId());
        artistInstrumentRepository.deleteByArtistProfile_ArtistProfileId(profile.getArtistProfileId());
        artistInstrumentRepository.flush(); // delete 먼저 db에 반영

//        // 최종 리스트로 재저장
        List<ArtistInstrument> mappings = instruments.stream()
                .map(inst -> new ArtistInstrument(profile, inst))
                .toList();
        artistInstrumentRepository.saveAll(mappings);
//
//        return new ApiRespDto<>("success", "악기 목록이 저장되었습니다.", toResponse(profile));
        // 세션 없다고 터짐
        // 응답은 방금 검증/조회한 instruments 로 만들기 (LAZY 컬렉션 접근 x)
        List<InstrumentResponse> instrumentResponses = instruments.stream()
                .map(i -> new InstrumentResponse(i.getInstId(), i.getInstName()))
                .toList();

        ArtistProfileResponse resp = new ArtistProfileResponse(
                profile.getArtistProfileId(),
                principalUser.getUserId(),
                profile.getBio(),
                profile.getCareer(),
                profile.getMajorName(),
                profile.getUser().getArtistStatus(), // 상태 소유자 = 유저 이기때문에 dto 내에 있어도 이렇게 가져와야함
                instrumentResponses
        );

        return new ApiRespDto<>("success", "악기 목록이 저장되었습니다.", resp);
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
        List<InstrumentResponse> instruments = profile.getArtistInstruments().stream()
                .map(ai -> new InstrumentResponse(
                        ai.getInstrument().getInstId(),
                        ai.getInstrument().getInstName()
                ))
                .toList();

        return new ArtistProfileResponse(
                profile.getArtistProfileId(),
                profile.getUser().getUserId(),
                profile.getBio(),
                profile.getCareer(),
                profile.getMajorName(),
                profile.getUser().getArtistStatus(),
                instruments

        );
    }

}
