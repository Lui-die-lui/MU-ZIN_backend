package com.muzin.mu.zin.service.artistProfile;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.artist.ArtistStyleSetRequest;
import com.muzin.mu.zin.dto.lesson.LessonStyleTagResponse;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.artist.ArtistStyleMap;
import com.muzin.mu.zin.entity.lesson.LessonStyleTag;
import com.muzin.mu.zin.repository.artist.ArtistProfileRepository;
import com.muzin.mu.zin.repository.artist.ArtistStyleMapRepository;
import com.muzin.mu.zin.repository.lesson.LessonStyleTagRepository;
import com.muzin.mu.zin.security.model.PrincipalUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArtistStyleService {


    private final ArtistProfileRepository artistProfileRepository;
    private final LessonStyleTagRepository lessonStyleTagRepository;
    private final ArtistStyleMapRepository artistStyleMapRepository;

    // 스타일 태그 목록 가져오기
    @Transactional(readOnly = true)
    public ApiRespDto<List<LessonStyleTagResponse>> getMyStyleTags(PrincipalUser principal) {
        ArtistProfile profile = artistProfileRepository.findByUser_UserId(principal.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("아티스트 프로필이 없습니다."));

        List<LessonStyleTagResponse> result = artistStyleMapRepository
                .findAllByArtistProfile_ArtistProfileId(profile.getArtistProfileId())
                .stream()
                .map(m -> new LessonStyleTagResponse( // 새 dto 만들어줌
                        m.getLessonStyleTag().getLessonStyleTagId(),
                        m.getLessonStyleTag().getStyleName()
                ))
                .toList();

        return new ApiRespDto<>("success","",result);
    }

    // 스타일 태그 수정 및 생성
    @Transactional
    public ApiRespDto<List<LessonStyleTagResponse>> setMyStyleTags(PrincipalUser principal, ArtistStyleSetRequest req) {
        ArtistProfile profile = artistProfileRepository.findByUser_UserId(principal.getUserId())
                .orElseThrow(()-> new IllegalArgumentException("아티스트 프로필이 없습니다."));

        List<Long> ids = (req.styleTagIds() == null) ? List.of() : req.styleTagIds().stream().distinct().toList();

        if (ids.size() > 5) {
            throw  new IllegalArgumentException("스타일 태그는 최대 5개까지 선택 가능합니다.");
        }

        // 기존 매핑 전부 삭제 후 set
        artistStyleMapRepository.deleteByArtistProfile_ArtistProfileId(profile.getArtistProfileId());
        artistStyleMapRepository.flush();

        if (ids.isEmpty()) {
            return new ApiRespDto<>("success","아티스트 스타일 태그가 비어있습니다.", List.of());
        }

        // 태그 엔티티 조회
        List<LessonStyleTag> tags = lessonStyleTagRepository.findAllById(ids);
        if (tags.size() != ids.size()){ // 실제 조회댄 태그 개수
            return new ApiRespDto<>("failed", "존재하지 않는 스타일 태그가 포함되어 있습니다.", List.of());
        }

        // 재생성(수정)
        List<ArtistStyleMap> maps = tags.stream()
                .map(tag -> ArtistStyleMap.builder()
                        .artistProfile(profile)
                        .lessonStyleTag(tag)
                        .build())
                .toList();

        artistStyleMapRepository.saveAll(maps);

        List<LessonStyleTagResponse> resp = tags.stream()
                .map(t -> new LessonStyleTagResponse(t.getLessonStyleTagId(), t.getStyleName()))
                .toList();

        return new ApiRespDto<>("success", "아티스트 스타일 태그가 저장되었습니다.", resp);

    }
}
