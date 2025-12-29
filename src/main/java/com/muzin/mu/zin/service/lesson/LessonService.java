package com.muzin.mu.zin.service.lesson;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.*;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.repository.ArtistProfileRepository;
import com.muzin.mu.zin.repository.lesson.LessonRepository;
import com.muzin.mu.zin.repository.lesson.LessonStyleMapRepository;
import com.muzin.mu.zin.security.model.PrincipalUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonRepository lessonRepository;
    private final LessonStyleMapRepository lessonStyleMapRepository;
    private final ArtistProfileRepository artistProfileRepository;

    // 새 레슨 만들기
    @Transactional
    public ApiRespDto<LessonCreateResponse> createLesson(LessonCreateRequest req, PrincipalUser principalUser) {

        ArtistProfile profile = artistProfileRepository.findByUser_UserId(principalUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("아티스트 프로필이 없습니다."));

        Lesson lesson = Lesson.builder()
                .artistProfile(profile)
                .title(req.title())
                .mode(req.mode())
                .durationMin(req.durationMin())
                .price(req.price())
                .description(req.description())
                .requirementText(req.requirementText())
                .build();

        Lesson saved = lessonRepository.save(lesson);

        return new ApiRespDto<>("success", "레슨이 생성되었습니다.", new LessonCreateResponse(
                saved.getLessonId(), saved.getTitle(), saved.getMode(), saved.getStatus()));
    }

    // 아티스트 본인 레슨인지 검증 + 수정
    @Transactional
    public ApiRespDto<?> updateLesson(Long lessonId, LessonUpdateRequest req, PrincipalUser principalUser) {

        ArtistProfile profile = artistProfileRepository.findByUser_UserId(principalUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("아티스트 프로필이 없습니다."));

        Lesson lesson = lessonRepository.findByLessonIdAndArtistProfile_ArtistProfileId(lessonId, profile.getArtistProfileId())
                .orElseThrow(() -> new IllegalArgumentException("레슨이 없거나 권한이 없습니다."));

        lesson.applyUpdate(req.title(), req.mode(), req.description(), req.requirementText(),req.price(), req.durationMin());

        // 일단 status도 같이 바꿔줌
        if (req.status() != null) {
            lesson.changeStatus(req.status());
        }

        // 스타일 태그도 같이 내려주기
        List<LessonStyleTagResponse> styleTags = lessonStyleMapRepository
                .findAllByLesson_LessonId(lesson.getLessonId())
                .stream()
                .map(m -> new LessonStyleTagResponse(
                        m.getLessonStyleTag().getLessonStyleTagId(),
                        m.getLessonStyleTag().getStyleName()
                ))
                .toList();

        ArtistLessonResponse resp = new ArtistLessonResponse(
                lesson.getLessonId(),
                lesson.getTitle(),
                lesson.getDescription(),
                lesson.getRequirementText(),
                lesson.getPrice(),
                lesson.getDurationMin(),
                lesson.getMode(),
                lesson.getStatus(),
                styleTags,
                lesson.getCreateDt(),
                lesson.getUpdateDt()
        );


        return new ApiRespDto<>("success", "레슨이 수정되었습니다.", resp);
    }

    // soft delete
    @Transactional
    public ApiRespDto<?> deleteLesson(Long lessonId, PrincipalUser principalUser) {

        ArtistProfile profile = artistProfileRepository.findByUser_UserId(principalUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("아티스트 프로필이 없습니다."));

        Lesson lesson = lessonRepository.findByLessonIdAndArtistProfile_ArtistProfileId(lessonId, profile.getArtistProfileId())
                .orElseThrow(() -> new IllegalArgumentException("레슨이 없거나 권한이 없습니다."));

        if (lesson.isDeleted()) {
            return new ApiRespDto<>("success", "이미 삭제된 레슨입니다.", null);
        }

        lesson.markDeleted(); // INACTIVE 상태로 바꿔줌
        return new ApiRespDto<>("success", "레슨이 삭제되었습니다.", null);
    }


    // 아티스트 내 레슨 목록 조회
    @Transactional(readOnly = true)
    public ApiRespDto<List<ArtistLessonResponse>> getArtistLesson(PrincipalUser principalUser) {

        ArtistProfile profile = artistProfileRepository.findByUser_UserId(principalUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("아티스트 프로필이 없습니다."));

        // 레슨 스타일 태그 리스트
        List<Lesson> lessons = lessonRepository
                .findAllByArtistProfile_ArtistProfileIdAndDeletedDtIsNullOrderByLessonIdDesc(profile.getArtistProfileId());

        List<ArtistLessonResponse> resp = lessons.stream().map(l-> {
            List<LessonStyleTagResponse> styleTags = lessonStyleMapRepository
                    .findAllByLesson_LessonId(l.getLessonId())
                    .stream()
                    .map(m->new LessonStyleTagResponse(
                            m.getLessonStyleTag().getLessonStyleTagId(),
                            m.getLessonStyleTag().getStyleName()
                    ))
                    .toList();

            return new ArtistLessonResponse(
                    l.getLessonId(),
                    l.getTitle(),
                    l.getDescription(),
                    l.getRequirementText(),
                    l.getPrice(),
                    l.getDurationMin(),
                    l.getMode(),
                    l.getStatus(),
                    styleTags,
                    l.getCreateDt(),
                    l.getUpdateDt()
            );
        }).toList();


        return new ApiRespDto<>("success", "", resp);
    }

    // 아티스트 레슨 단일 조회
    @Transactional(readOnly = true)
    public ApiRespDto<ArtistLessonResponse> getArtistLessonDetail(Long lessonId, PrincipalUser principalUser) {

        ArtistProfile profile = artistProfileRepository.findByUser_UserId(principalUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("아티스트 프로필이 없습니다."));

        Lesson lesson = lessonRepository.findByLessonIdAndArtistProfile_ArtistProfileId(lessonId, profile.getArtistProfileId())
                .orElseThrow(() -> new IllegalArgumentException("레슨이 없거나 권한이 없습니다."));

        // 혹시 모르니까 걸어놓기(화면에 보여주진 않을건데 어떻게든 접근 할 가능성 때문에)
        if (lesson.isDeleted()) {
            throw new IllegalArgumentException("삭제된 레슨입니다.");
        }

        // 이거 api를 따로 빼고 구현해서 계속 이래야함;
        List<LessonStyleTagResponse> styleTags = lessonStyleMapRepository
                .findAllByLesson_LessonId(lesson.getLessonId())
                .stream()
                .map(m -> new LessonStyleTagResponse(
                        m.getLessonStyleTag().getLessonStyleTagId(),
                        m.getLessonStyleTag().getStyleName()
                ))
                .toList();

        ArtistLessonResponse resp = new ArtistLessonResponse(
                lesson.getLessonId(),
                lesson.getTitle(),
                lesson.getDescription(),
                lesson.getRequirementText(),
                lesson.getPrice(),
                lesson.getDurationMin(),
                lesson.getMode(),
                lesson.getStatus(),
                styleTags,
                lesson.getCreateDt(),
                lesson.getUpdateDt()
        );

        return new ApiRespDto<>("success","조회 완료", resp);
    }

}
