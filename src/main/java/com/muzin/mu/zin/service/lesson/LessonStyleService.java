package com.muzin.mu.zin.service.lesson;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.LessonStyleTagResponse;
import com.muzin.mu.zin.dto.lesson.SetLessonStylesRequest;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.lesson.LessonStyleMap;
import com.muzin.mu.zin.entity.lesson.LessonStyleTag;
import com.muzin.mu.zin.repository.ArtistProfileRepository;
import com.muzin.mu.zin.repository.lesson.LessonRepository;
import com.muzin.mu.zin.repository.lesson.LessonStyleMapRepository;
import com.muzin.mu.zin.repository.lesson.LessonStyleTagRepository;
import com.muzin.mu.zin.security.model.PrincipalUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LessonStyleService {

    private final LessonStyleTagRepository lessonStyleTagRepository;
    private final LessonStyleMapRepository lessonStyleMapRepository;
    private final LessonRepository lessonRepository;
    private final ArtistProfileRepository artistProfileRepository;

    // 레슨 스타일 태그 불러오기
    @Transactional(readOnly = true)
    public ApiRespDto<List<LessonStyleTagResponse>> getLessonStyleTags() {
        List<LessonStyleTagResponse> data = lessonStyleTagRepository.findAllByOrderByStyleNameAsc()
                .stream()
                .map(tag -> new LessonStyleTagResponse(tag.getLessonStyleTagId(),tag.getStyleName()))
                .toList();

        return new ApiRespDto<>("success", "", data);
    }

    // 아티스트 본인 레슨 스타일 지정
    @Transactional
    public ApiRespDto<List<LessonStyleTagResponse>> setLessonStyles(Long lessonId, SetLessonStylesRequest req, PrincipalUser principalUser) {

        // 내 artistProfile 찾기
        ArtistProfile profile = artistProfileRepository.findByUser_UserId(principalUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("아티스트 프로필이 없습니다."));

        // 내 레슨인지 체크
        Lesson lesson = lessonRepository.findByLessonIdAndArtistProfile_ArtistProfileId(lessonId, profile.getArtistProfileId())
                .orElseThrow(() -> new IllegalArgumentException("레슨이 없거나 권한이 없습니다."));

        // 요청 ids 정리(null방지, 중복 제거) - 이거 설명 필요함
        List<Long> ids = Optional.ofNullable(req.styleTagIds()).orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 태그 존재 검증 - 비어있으면(존재하지 않으면) 그냥 리스트 보여주기? 있으면 아이디에 해당하는 태그들 보여줌?
        List<LessonStyleTag> tags = ids.isEmpty() ? List.of() : lessonStyleTagRepository.findAllById(ids);

        if (tags.size() != ids.size()) {
            throw new IllegalArgumentException("존재하지 않는 수업방식 태그가 포함되어있습니다.");
        }

        // 전체 교체 : 삭제 -> flush -> 재삽입(artistInstrument 쪽이랑 동일하게 flush로 감)
        lessonStyleMapRepository.deleteByLesson_LessonId(lessonId); // 지우고
        lessonStyleMapRepository.flush(); // Uk 충돌/순서 꼬임방지 - 지우는걸 확실하게 먼저 해줌

        List<LessonStyleMap> mappings = tags.stream()
                .map(tag -> LessonStyleMap.builder()
                        .lesson(lesson)
                        .lessonStyleTag(tag)
                        .build())
                .toList();  // 이쪽 설명 필요함

        lessonStyleMapRepository.saveAll(mappings);

        // 응답은 LAZY 컬렉션 건드리지 않고, 방금 조회한 tags 기반으로 구성
        List<LessonStyleTagResponse> resp = tags.stream()
                .map(tag -> new LessonStyleTagResponse(tag.getLessonStyleTagId(), tag.getStyleName()))
                .toList();

        return new ApiRespDto<>("success", "레슨 수업방식이 변경되었습니다.", resp);

    }
}
