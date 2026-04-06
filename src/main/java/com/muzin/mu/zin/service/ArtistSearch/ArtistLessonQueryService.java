package com.muzin.mu.zin.service.ArtistSearch;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.artist.ArtistInstrumentSummary;
import com.muzin.mu.zin.dto.artist.ArtistLessonCardResponse;
import com.muzin.mu.zin.dto.artist.ArtistLessonDetailResponse;
import com.muzin.mu.zin.dto.lesson.LessonCardRow;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.repository.lesson.LessonRepository;
import com.muzin.mu.zin.repository.lesson.LessonRepositoryCustom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArtistLessonQueryService {

    private final LessonRepository lessonRepository;
    private final LessonRepositoryCustom lessonRepositoryCustom;


    @Transactional(readOnly = true)
    public ApiRespDto<List<ArtistLessonCardResponse>> getArtistLessonCards(Long artistProfileId) {
        List<ArtistLessonCardResponse> resp = lessonRepositoryCustom.findArtistLessonCardRowDsl(artistProfileId)
                .stream()
                .map(this::toArtistLessonCardResponse)
                .toList();

        return new ApiRespDto<>("success", "", resp);
    }

    @Transactional(readOnly = true)
    public ApiRespDto<ArtistLessonDetailResponse> getArtistLessonDetail(Long artistProfileId, Long lessonId) {
        Lesson lesson = lessonRepositoryCustom.findArtistLessonDetailDsl(artistProfileId, lessonId)
                .orElseThrow(() -> new IllegalArgumentException("해당 레슨을 찾을 수 없습니다."));
        ArtistLessonDetailResponse resp = toArtistLessonDetailResponse(lesson);
        return new ApiRespDto<>("success", "", resp);
    }

    private ArtistLessonCardResponse toArtistLessonCardResponse(LessonCardRow row) {
        return new ArtistLessonCardResponse(
                row.lessonId(),
                row.title(),
                row.mode(),
                row.price(),
                row.durationMin(),
                new ArtistInstrumentSummary(row.instId(), row.instName())
        );
    }

    private ArtistLessonDetailResponse toArtistLessonDetailResponse(Lesson lesson) {
        return new ArtistLessonDetailResponse(
                lesson.getLessonId(),
                lesson.getTitle(),
                lesson.getDescription(),
                lesson.getRequirementText(),
                lesson.getPrice(),
                lesson.getDurationMin(),
                lesson.getMode(),
                lesson.getStatus(),

                // 현재 레슨1개당 1개 악기만 매핑되기 때문에 List -> 단일 조회로 바꿈
                new ArtistInstrumentSummary(
                        lesson.getInstrument().getInstId(),
                        lesson.getInstrument().getInstName()
                )
        );
    }

}
