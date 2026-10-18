package com.muzin.mu.zin.service.lesson;

import com.muzin.mu.zin.dto.lesson.LessonRecommendRequest;
import com.muzin.mu.zin.dto.lesson.RecommendedLessonResponse;
import com.muzin.mu.zin.dto.region.SearchMainRegionSummary;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.artist.ArtistServiceRegion;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonStyleMap;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.artist.ArtistProfileRepository;
import com.muzin.mu.zin.repository.lesson.LessonRepositoryCustom;
import com.muzin.mu.zin.repository.lesson.LessonSearchCond;
import com.muzin.mu.zin.repository.lesson.LessonStyleMapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/*
 * 음악 취미 찾기 -> 선택 지역의 실제 레슨 추천
 *  1) 필수 조건으로 거름: 공개(ACTIVE)·삭제 안 됨·승인 아티스트·자동 비공개 아님 (기존 공개 레슨 검색과 같은 조건)
 *                        + 추천 취미의 악기 + 선택 지역(아티스트 대표 지역 또는 활동 가능 지역)
 *  2) 걸러진 레슨 안에서만 취향 선호로 정렬: 스타일 태그 일치 x2 + 원하는 수업 방식 x1
 *     -> 선호 점수가 높아도 필수 조건에 맞지 않는 레슨은 결과에 없음
 *  3) 같은 점수는 최신 레슨(id 큰 순)으로 -> 같은 요청엔 항상 같은 순서
 *  - 다른 지역으로 조건을 넓히지 않음 (결과가 없으면 빈 목록)
 *  - 샘플(시연용) 계정 레슨: app.sample-data.show=false(기본·운영)면 제외, true(로컬)면 sample=true 로 표시
 *  - 거리 좌표를 쓰지 않으므로 "가까운 순"이 아님
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LessonRecommendService {

    @Value("${app.sample-data.show:false}")
    private boolean showSampleData;

    private static final int MAX_INST = 20;
    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 30;
    private static final int SCAN_LIMIT = 200;

    private final LessonRepositoryCustom lessonRepositoryCustom;
    private final LessonStyleMapRepository lessonStyleMapRepository;
    private final ArtistProfileRepository artistProfileRepository;

    public List<RecommendedLessonResponse> recommend(LessonRecommendRequest req) {
        List<Long> instIds = req.instIds() == null ? List.of() : req.instIds().stream().filter(Objects::nonNull).distinct().toList();
        String region1 = trimToNull(req.region1DepthName());
        if (instIds.isEmpty() || instIds.size() > MAX_INST) {
            throw ApiException.badRequest("추천할 악기를 1개 이상 골라주세요.");
        }
        if (region1 == null) {
            throw ApiException.badRequest("시·도를 골라주세요.");
        }
        String region2 = trimToNull(req.region2DepthName());
        String region3 = region2 == null ? null : trimToNull(req.region3DepthName());
        int size = req.size() == null ? DEFAULT_SIZE : Math.min(Math.max(req.size(), 1), MAX_SIZE);

        // 1) 필수 조건 (기존 공개 레슨 검색 쿼리 그대로)
        LessonSearchCond cond = new LessonSearchCond(null, null, null, null, instIds,
                region1, region2, region3, null, null, null, null);
        List<Lesson> lessons = lessonRepositoryCustom.searchPublicLessons(cond, PageRequest.of(0, SCAN_LIMIT)).stream()
                .filter(l -> showSampleData || !l.getArtistProfile().getUser().isSample())
                .toList();
        if (lessons.isEmpty()) {
            return List.of();
        }

        // 표시용 데이터 한 번에
        List<Long> lessonIds = lessons.stream().map(Lesson::getLessonId).toList();
        Map<Long, List<RecommendedLessonResponse.StyleTag>> tagsByLesson = lessonStyleMapRepository.findAllWithTagByLessonIds(lessonIds)
                .stream()
                .sorted(Comparator.comparing((LessonStyleMap m) -> m.getLessonStyleTag().getLessonStyleTagId()))
                .collect(Collectors.groupingBy(m -> m.getLesson().getLessonId(), LinkedHashMap::new,
                        Collectors.mapping(m -> new RecommendedLessonResponse.StyleTag(
                                m.getLessonStyleTag().getLessonStyleTagId(), m.getLessonStyleTag().getStyleName()), Collectors.toList())));

        Set<Long> preferTags = req.preferTagIds() == null ? Set.of() : new HashSet<>(req.preferTagIds());
        Set<LessonMode> preferModes = req.preferModes() == null ? Set.of() : new HashSet<>(req.preferModes());

        return lessons.stream()
                .map(l -> toResponse(l, tagsByLesson.getOrDefault(l.getLessonId(), List.of()), preferTags, preferModes, region1, region2, region3))
                // 2) 선호 점수 높은 순 -> 3) 같으면 최신 레슨
                .sorted(Comparator.comparingInt(RecommendedLessonResponse::preferenceScore).reversed()
                        .thenComparing(RecommendedLessonResponse::lessonId, Comparator.reverseOrder()))
                .limit(size)
                .toList();
    }

    private RecommendedLessonResponse toResponse(Lesson lesson, List<RecommendedLessonResponse.StyleTag> tags,
                                                 Set<Long> preferTags, Set<LessonMode> preferModes,
                                                 String region1, String region2, String region3) {
        ArtistProfile ap = lesson.getArtistProfile();
        List<Long> matched = tags.stream().map(RecommendedLessonResponse.StyleTag::lessonStyleTagId).filter(preferTags::contains).toList();
        boolean modeMatched = preferModes.contains(lesson.getMode());
        int score = matched.size() * 2 + (modeMatched ? 1 : 0);

        boolean main = Objects.equals(region1, ap.getRegion1DepthName())
                && (region2 == null || Objects.equals(region2, ap.getRegion2DepthName()))
                && (region3 == null || Objects.equals(region3, ap.getRegion3DepthName()));

        return new RecommendedLessonResponse(
                lesson.getLessonId(),
                lesson.getTitle(),
                lesson.getDescription(),
                lesson.getPrice(),
                lesson.getDurationMin(),
                lesson.getMode(),
                lesson.getInstrument().getInstId(),
                lesson.getInstrument().getInstName(),
                ap.getArtistProfileId(),
                ap.getUser().getUsername(),
                ap.getUser().getProfileImgUrl(),
                new SearchMainRegionSummary(ap.getRegion1DepthName(), ap.getRegion2DepthName(), ap.getRegion3DepthName(), ap.getAddressLabel()),
                main ? "MAIN" : "SERVICE",
                tags,
                matched,
                modeMatched,
                score,
                ap.getUser().isSample()
        );
    }

    private static String trimToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
