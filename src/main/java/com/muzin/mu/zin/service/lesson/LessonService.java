package com.muzin.mu.zin.service.lesson;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.*;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.lesson.LessonStatus;
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
                .status(LessonStatus.ACTIVE)
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

        lesson.applyUpdate(req.title(), req.mode());

        // 일단 status도 같이 바꿔줌
        if (req.status() != null) {
            lesson.changeStatus(req.status());
        }

        return new ApiRespDto<>("success", "레슨이 수정되었습니다.", null);
    }


    // 아티스트 내 레슨 목록 조회
    @Transactional(readOnly = true)
    public ApiRespDto<List<ArtistLessonResponse>> getArtistLesson(PrincipalUser principalUser) {

        ArtistProfile profile = artistProfileRepository.findByUser_UserId(principalUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("아티스트 프로필이 없습니다."));

        // 레슨 스타일 태그 리스트
        List<Lesson> lessons = lessonRepository
                .findAllByArtistProfile_ArtistProfileIdOrderByLessonIdDesc(profile.getArtistProfileId());

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
                    l.getMode(),
                    styleTags,
                    l.getCreateDt(),
                    l.getUpdateDt()
            );
        }).toList();


        return new ApiRespDto<>("success", "", resp);
    }
}
