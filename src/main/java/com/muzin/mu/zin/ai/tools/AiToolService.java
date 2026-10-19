package com.muzin.mu.zin.ai.tools;

import com.muzin.mu.zin.ai.config.AiProperties;
import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.Review.ReviewSummaryResponse;
import com.muzin.mu.zin.dto.artist.ArtistInstrumentRow;
import com.muzin.mu.zin.dto.artist.ArtistSearchRequest;
import com.muzin.mu.zin.dto.artist.ArtistSearchRow;
import com.muzin.mu.zin.dto.lesson.LessonCardRow;
import com.muzin.mu.zin.dto.region.RegionOptionDto;
import com.muzin.mu.zin.dto.region.SearchServiceRegionRow;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.ArtistStatus;
import com.muzin.mu.zin.entity.TimePart;
import com.muzin.mu.zin.entity.artist.ArtistServiceRegion;
import com.muzin.mu.zin.entity.instrument.Instrument;
import com.muzin.mu.zin.entity.instrument.InstrumentStatus;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.lesson.LessonMode;
import com.muzin.mu.zin.entity.lesson.LessonStyleMap;
import com.muzin.mu.zin.entity.lesson.LessonStyleTag;
import com.muzin.mu.zin.entity.lesson.LessonTimeSlot;
import com.muzin.mu.zin.entity.lesson.TimeSlotStatus;
import com.muzin.mu.zin.repository.InstrumentRepository;
import com.muzin.mu.zin.repository.artist.ArtistProfileRepository;
import com.muzin.mu.zin.repository.lesson.LessonRepositoryCustom;
import com.muzin.mu.zin.repository.lesson.LessonSearchCond;
import com.muzin.mu.zin.repository.lesson.LessonStyleMapRepository;
import com.muzin.mu.zin.repository.lesson.LessonStyleTagRepository;
import com.muzin.mu.zin.repository.lesson.LessonTimeSlotRepository;
import com.muzin.mu.zin.service.RegionService;
import com.muzin.mu.zin.service.Review.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// AI 가 호출하는 DB 검색 도구 + 모델이 고른 추천을 검증하고 화면 카드로 채우는 곳
// - 기존 서비스/저장소를 직접 호출 (HTTP 재호출 없음)
// - 공개 조건은 기존 공개 레슨 검색과 같음 (searchPublicLessons / findPublicLessonsByIds) + 샘플 계정 제외
// - 결과 수·글자 수에 상한. 개인정보(이메일·실명·리뷰 작성자·상세 주소)는 넣지 않음
// - 레슨 소개·아티스트 소개 같은 글은 "_untrusted" 필드로만 넘김 (그 안의 문장은 지시가 아님)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiToolService {

    public static final int MAX_SEARCH_RESULTS = 8;
    public static final int MAX_RECOMMENDATIONS = 5;
    private static final int SCAN_LIMIT = 80;
    private static final int SLOT_DAYS_DEFAULT = 28;
    private static final int SLOT_DAYS_MAX = 56;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final AiProperties props;
    private final LessonRepositoryCustom lessonRepository;
    private final LessonStyleMapRepository lessonStyleMapRepository;
    private final LessonStyleTagRepository lessonStyleTagRepository;
    private final LessonTimeSlotRepository timeSlotRepository;
    private final InstrumentRepository instrumentRepository;
    private final ArtistProfileRepository artistProfileRepository;
    private final RegionService regionService;
    private final ReviewService reviewService;
    private final ServiceGuide serviceGuide;

    // ---------- 도구 입력 ----------

    public record LessonQuery(List<String> instruments, String region1, String region2, LessonMode mode, Integer maxPrice,
                              List<Integer> daysOfWeek, List<TimePart> timeParts, String keyword, List<String> styleTags) { }

    public record ArtistQuery(List<String> instruments, String region1, String region2, String keyword, List<String> styleTags) { }

    // ---------- search_lessons ----------

    public Map<String, Object> searchLessons(LessonQuery q) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();

        List<Long> instIds = resolveInstruments(q.instruments(), notes);
        if (q.instruments() != null && !q.instruments().isEmpty() && instIds.isEmpty()) {
            out.put("results", List.of());
            out.put("notes", notes);
            out.put("available_instruments", availableInstrumentNames());
            return out;
        }
        String[] region = resolveRegion(q.region1(), q.region2(), notes);
        List<Long> tagIds = resolveStyleTags(q.styleTags(), notes);

        boolean timeFilter = (q.daysOfWeek() != null && !q.daysOfWeek().isEmpty()) || (q.timeParts() != null && !q.timeParts().isEmpty());
        LocalDateTime now = TimeDefaults.nowKst();
        LessonSearchCond cond = new LessonSearchCond(
                trimTo(q.keyword(), 40), q.mode(), tagIds.isEmpty() ? null : tagIds, null, instIds.isEmpty() ? null : instIds,
                region[0], region[1], null,
                // 요일·시간대 조건은 "지금부터 4주 안의 예약 가능 시간"으로만 (지난 시간 제외)
                timeFilter ? now : null, timeFilter ? now.plusDays(SLOT_DAYS_DEFAULT) : null,
                q.daysOfWeek() == null ? null : q.daysOfWeek().stream().filter(d -> d != null && d >= 1 && d <= 7).distinct().toList(),
                q.timeParts());

        List<Lesson> lessons = visible(lessonRepository.searchPublicLessons(cond, PageRequest.of(0, SCAN_LIMIT))).stream()
                .filter(l -> q.maxPrice() == null || (l.getPrice() != null && l.getPrice() <= q.maxPrice()))
                .limit(MAX_SEARCH_RESULTS)
                .toList();

        Map<Long, List<String>> tags = lessonTags(lessons);
        Map<Long, List<ArtistServiceRegion>> serviceRegions = serviceRegions(lessons.stream().map(l -> l.getArtistProfile().getArtistProfileId()).toList());
        out.put("applied_filters", appliedFilters(q, region, instIds, tagIds));
        out.put("results", lessons.stream().map(l -> lessonSummary(l, tags, serviceRegions, now)).toList());
        if (!notes.isEmpty()) out.put("notes", notes);
        out.put("result_limit", MAX_SEARCH_RESULTS);
        return out;
    }

    // ---------- get_lesson ----------

    public Map<String, Object> getLesson(long lessonId) {
        Optional<Lesson> found = publicLessons(List.of(lessonId)).stream().findFirst();
        if (found.isEmpty()) return Map.of("error", "NOT_FOUND", "message", "공개된 레슨이 아니거나 없는 레슨입니다.");
        Lesson l = found.get();
        LocalDateTime now = TimeDefaults.nowKst();
        Map<String, Object> out = lessonSummary(l, lessonTags(List.of(l)),
                serviceRegions(List.of(l.getArtistProfile().getArtistProfileId())), now);
        out.put("description_untrusted", untrusted(l.getDescription(), 600));
        out.put("requirement_untrusted", untrusted(l.getRequirementText(), 300));
        out.put("reviews", reviewSummary(reviewService.getLessonReviews(lessonId).summary()));
        return out;
    }

    // ---------- get_open_slots ----------

    public Map<String, Object> getOpenSlots(long lessonId, Integer daysAhead, Integer limit) {
        if (publicLessons(List.of(lessonId)).isEmpty()) {
            return Map.of("error", "NOT_FOUND", "message", "공개된 레슨이 아니거나 없는 레슨입니다.");
        }
        int days = daysAhead == null ? SLOT_DAYS_DEFAULT : Math.min(Math.max(daysAhead, 1), SLOT_DAYS_MAX);
        int max = limit == null ? 10 : Math.min(Math.max(limit, 1), 10);
        List<LessonTimeSlot> slots = futureOpenSlots(lessonId, days);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lesson_id", lessonId);
        out.put("checked_at", TimeDefaults.nowKst().format(DT));
        out.put("range_days", days);
        out.put("open_slot_count", slots.size());
        out.put("slots", slots.stream().limit(max).map(s -> Map.of("start", s.getStartDt().format(DT), "end", s.getEndDt().format(DT))).toList());
        return out;
    }

    // ---------- search_artists ----------

    public Map<String, Object> searchArtists(ArtistQuery q) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        List<Long> instIds = resolveInstruments(q.instruments(), notes);
        if (q.instruments() != null && !q.instruments().isEmpty() && instIds.isEmpty()) {
            out.put("results", List.of());
            out.put("notes", notes);
            out.put("available_instruments", availableInstrumentNames());
            return out;
        }
        String[] region = resolveRegion(q.region1(), q.region2(), notes);
        List<Long> tagIds = resolveStyleTags(q.styleTags(), notes);
        ArtistSearchRequest req = new ArtistSearchRequest(trimTo(q.keyword(), 40), null, instIds.isEmpty() ? null : instIds,
                tagIds.isEmpty() ? null : tagIds, region[0], region[1], null, 0, SCAN_LIMIT, false);
        List<ArtistSearchRow> rows = artistProfileRepository.searchArtistRows(req, PageRequest.of(0, SCAN_LIMIT), null);
        Map<Long, ArtistProfile> profiles = visibleArtists(rows.stream().map(ArtistSearchRow::artistProfileId).toList());
        List<ArtistSearchRow> shown = rows.stream().filter(r -> profiles.containsKey(r.artistProfileId())).limit(MAX_SEARCH_RESULTS).toList();
        List<Long> ids = shown.stream().map(ArtistSearchRow::artistProfileId).toList();
        Map<Long, List<String>> insts = artistInstruments(ids);
        Map<Long, List<String>> regions = artistServiceRegionLabels(ids);
        out.put("results", shown.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("artist_profile_id", r.artistProfileId());
            m.put("name", r.username());
            m.put("major", r.majorName());
            m.put("instruments", insts.getOrDefault(r.artistProfileId(), List.of()));
            m.put("main_region", regionLabel(r.region1DepthName(), r.region2DepthName()));
            m.put("service_regions", regions.getOrDefault(r.artistProfileId(), List.of()));
            m.put("public_lesson_count", publicLessonCards(r.artistProfileId()).size());
            m.put("bio_untrusted", untrusted(profiles.get(r.artistProfileId()).getBio(), 160));
            return m;
        }).toList());
        if (!notes.isEmpty()) out.put("notes", notes);
        out.put("result_limit", MAX_SEARCH_RESULTS);
        return out;
    }

    // ---------- get_artist ----------

    public Map<String, Object> getArtist(long artistProfileId) {
        Optional<ArtistProfile> found = artistProfileRepository.findWithDetailByArtistProfileId(artistProfileId)
                .filter(this::isVisibleArtist);
        if (found.isEmpty()) return Map.of("error", "NOT_FOUND", "message", "공개된 아티스트가 아니거나 없는 아티스트입니다.");
        ArtistProfile ap = found.get();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("artist_profile_id", ap.getArtistProfileId());
        out.put("name", ap.getUser().getUsername());
        out.put("major", ap.getMajorName());
        out.put("instruments", ap.getArtistInstruments().stream().map(ai -> ai.getInstrument().getInstName()).distinct().toList());
        out.put("main_region", regionLabel(ap.getRegion1DepthName(), ap.getRegion2DepthName()));
        out.put("service_regions", artistServiceRegionLabels(List.of(artistProfileId)).getOrDefault(artistProfileId, List.of()));
        out.put("bio_untrusted", untrusted(ap.getBio(), 400));
        out.put("career_untrusted", untrusted(ap.getCareer(), 500));
        out.put("lessons", publicLessonCards(artistProfileId).stream().limit(10).map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lesson_id", c.lessonId());
            m.put("title", c.title());
            m.put("instrument", c.instName());
            m.put("price_krw", c.price());
            m.put("duration_min", c.durationMin());
            m.put("mode", c.mode());
            return m;
        }).toList());
        out.put("reviews", reviewSummary(reviewService.getArtistReviews(artistProfileId).summary()));
        return out;
    }

    // ---------- get_service_guide ----------

    public Map<String, Object> getServiceGuide(String topic) {
        Map<String, ServiceGuide.Section> sections = serviceGuide.sections();
        if (topic == null || topic.isBlank() || !sections.containsKey(topic.trim())) {
            Map<String, Object> out = new LinkedHashMap<>();
            if (topic != null && !topic.isBlank()) {
                out.put("error", "UNKNOWN_TOPIC");
                out.put("message", "이 주제의 안내는 서비스 안내 문서에 없습니다. 추측하지 말고 안내가 없다고 답하세요.");
            }
            out.put("topics", sections.values().stream().map(s -> Map.of("topic", s.topic(), "title", s.title())).toList());
            return out;
        }
        ServiceGuide.Section s = sections.get(topic.trim());
        return Map.of("topic", s.topic(), "title", s.title(), "guide", s.body());
    }

    public List<String> guideTopics() {
        return List.copyOf(serviceGuide.sections().keySet());
    }

    // ---------- 추천 검증 + 카드 ----------

    public enum Kind { LESSON, ARTIST }

    public record RawRecommendation(Kind kind, Long id, String reason) { }

    public record ValidRecommendation(Kind kind, long id, String reason) { }

    // 모델이 고른 추천 중 실제 공개된 것만 남김 (중복 제거, 최대 5개)
    public List<ValidRecommendation> validate(List<RawRecommendation> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<RawRecommendation> cand = raw.stream()
                .filter(r -> r != null && r.kind() != null && r.id() != null && r.id() > 0)
                .collect(Collectors.toMap(r -> r.kind() + ":" + r.id(), r -> r, (a, b) -> a, LinkedHashMap::new))
                .values().stream().limit(MAX_RECOMMENDATIONS * 2L).toList();
        Set<Long> lessonOk = publicLessons(cand.stream().filter(r -> r.kind() == Kind.LESSON).map(RawRecommendation::id).toList())
                .stream().map(Lesson::getLessonId).collect(Collectors.toSet());
        Set<Long> artistOk = visibleArtists(cand.stream().filter(r -> r.kind() == Kind.ARTIST).map(RawRecommendation::id).toList()).keySet();
        return cand.stream()
                .filter(r -> r.kind() == Kind.LESSON ? lessonOk.contains(r.id()) : artistOk.contains(r.id()))
                .limit(MAX_RECOMMENDATIONS)
                .map(r -> new ValidRecommendation(r.kind(), r.id(), AiText.clean(r.reason(), 200)))
                .toList();
    }

    public record SlotView(LocalDateTime startDt, LocalDateTime endDt) { }

    // 화면 카드: 값은 모두 지금 DB 에서 (모델이 만든 가격·링크·예약 가능 여부는 쓰지 않음)
    public record Card(String kind, long id, String reason, boolean available, String title, Long artistProfileId,
                       String artistName, String profileImgUrl, String majorName, List<String> instruments,
                       String mainRegion, List<String> serviceRegions, Integer price, Integer durationMin, LessonMode mode,
                       Integer openSlotCount, List<SlotView> nextSlots, Integer publicLessonCount, String link) { }

    public List<Card> cards(List<ValidRecommendation> recs) {
        if (recs == null || recs.isEmpty()) return List.of();
        LocalDateTime now = TimeDefaults.nowKst();
        Map<Long, Lesson> lessons = publicLessons(recs.stream().filter(r -> r.kind() == Kind.LESSON).map(ValidRecommendation::id).toList())
                .stream().collect(Collectors.toMap(Lesson::getLessonId, Function.identity()));
        Map<Long, ArtistProfile> artists = visibleArtists(recs.stream().filter(r -> r.kind() == Kind.ARTIST).map(ValidRecommendation::id).toList());
        List<Long> artistIdsForRegions = new ArrayList<>(artists.keySet());
        lessons.values().forEach(l -> artistIdsForRegions.add(l.getArtistProfile().getArtistProfileId()));
        Map<Long, List<String>> regions = artistServiceRegionLabels(artistIdsForRegions);
        Map<Long, List<String>> artistInsts = artistInstruments(new ArrayList<>(artists.keySet()));

        List<Card> out = new ArrayList<>();
        for (ValidRecommendation r : recs) {
            if (r.kind() == Kind.LESSON) {
                Lesson l = lessons.get(r.id());
                if (l == null) {
                    out.add(unavailable(r, "/lessons/" + r.id()));
                    continue;
                }
                ArtistProfile ap = l.getArtistProfile();
                List<LessonTimeSlot> slots = futureOpenSlots(l.getLessonId(), SLOT_DAYS_DEFAULT);
                out.add(new Card("LESSON", r.id(), r.reason(), true, l.getTitle(), ap.getArtistProfileId(), ap.getUser().getUsername(),
                        ap.getUser().getProfileImgUrl(), ap.getMajorName(), List.of(l.getInstrument().getInstName()),
                        regionLabel(ap.getRegion1DepthName(), ap.getRegion2DepthName()),
                        regions.getOrDefault(ap.getArtistProfileId(), List.of()), l.getPrice(), l.getDurationMin(), l.getMode(),
                        slots.size(), slots.stream().limit(3).map(s -> new SlotView(s.getStartDt(), s.getEndDt())).toList(), null,
                        "/lessons/" + l.getLessonId()));
            } else {
                ArtistProfile ap = artists.get(r.id());
                if (ap == null) {
                    out.add(unavailable(r, "/artists/" + r.id()));
                    continue;
                }
                out.add(new Card("ARTIST", r.id(), r.reason(), true, ap.getUser().getUsername(), ap.getArtistProfileId(),
                        ap.getUser().getUsername(), ap.getUser().getProfileImgUrl(), ap.getMajorName(),
                        artistInsts.getOrDefault(ap.getArtistProfileId(), List.of()),
                        regionLabel(ap.getRegion1DepthName(), ap.getRegion2DepthName()),
                        regions.getOrDefault(ap.getArtistProfileId(), List.of()), null, null, null, null, null,
                        publicLessonCards(ap.getArtistProfileId()).size(), "/artists/" + ap.getArtistProfileId()));
            }
        }
        return out;
    }

    // 예전 대화를 다시 볼 때 레슨이 내려갔거나 비공개가 된 경우
    private static Card unavailable(ValidRecommendation r, String link) {
        return new Card(r.kind().name(), r.id(), r.reason(), false, null, null, null, null, null, List.of(), null, List.of(),
                null, null, null, null, List.of(), null, null);
    }

    // ---------- 공통 ----------

    private List<Lesson> publicLessons(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return visible(lessonRepository.findPublicLessonsByIds(ids));
    }

    private List<Lesson> visible(List<Lesson> lessons) {
        return lessons.stream().filter(l -> props.isIncludeSampleData() || !l.getArtistProfile().getUser().isSample()).toList();
    }

    private boolean isVisibleArtist(ArtistProfile ap) {
        return ap.getUser().getArtistStatus() == ArtistStatus.APPROVED
                && !ap.getUser().isSuspended()
                && (props.isIncludeSampleData() || !ap.getUser().isSample());
    }

    private Map<Long, ArtistProfile> visibleArtists(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<Long, ArtistProfile> out = new LinkedHashMap<>();
        artistProfileRepository.findAllById(ids).stream().filter(this::isVisibleArtist)
                .forEach(ap -> out.put(ap.getArtistProfileId(), ap));
        return out;
    }

    private List<LessonCardRow> publicLessonCards(long artistProfileId) {
        return lessonRepository.findArtistLessonCardRowDsl(artistProfileId);
    }

    private List<LessonTimeSlot> futureOpenSlots(long lessonId, int days) {
        LocalDateTime now = TimeDefaults.nowKst();
        return timeSlotRepository.findAllByLesson_LessonIdAndStartDtBetweenOrderByStartDtAsc(lessonId, now, now.plusDays(days))
                .stream().filter(s -> s.getStatus() == TimeSlotStatus.OPEN && s.getStartDt().isAfter(now)).toList();
    }

    private Map<String, Object> lessonSummary(Lesson l, Map<Long, List<String>> tags, Map<Long, List<ArtistServiceRegion>> serviceRegions,
                                              LocalDateTime now) {
        ArtistProfile ap = l.getArtistProfile();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lesson_id", l.getLessonId());
        m.put("title", AiText.clean(l.getTitle(), 80));
        m.put("instrument", l.getInstrument().getInstName());
        m.put("artist", Map.of("artist_profile_id", ap.getArtistProfileId(), "name", ap.getUser().getUsername()));
        m.put("price_krw", l.getPrice());
        m.put("duration_min", l.getDurationMin());
        m.put("mode", l.getMode());
        m.put("main_region", regionLabel(ap.getRegion1DepthName(), ap.getRegion2DepthName()));
        m.put("service_regions", serviceRegions.getOrDefault(ap.getArtistProfileId(), List.of()).stream()
                .map(r -> regionLabel(r.getRegion1DepthName(), r.getRegion2DepthName())).distinct().limit(5).toList());
        m.put("style_tags", tags.getOrDefault(l.getLessonId(), List.of()));
        m.put("open_slots_next_4_weeks", futureOpenSlots(l.getLessonId(), SLOT_DAYS_DEFAULT).size());
        m.put("description_untrusted", untrusted(l.getDescription(), 160));
        return m;
    }

    private Map<Long, List<String>> lessonTags(List<Lesson> lessons) {
        if (lessons.isEmpty()) return Map.of();
        return lessonStyleMapRepository.findAllWithTagByLessonIds(lessons.stream().map(Lesson::getLessonId).toList()).stream()
                .collect(Collectors.groupingBy(m -> m.getLesson().getLessonId(),
                        Collectors.mapping((LessonStyleMap m) -> m.getLessonStyleTag().getStyleName(), Collectors.toList())));
    }

    private Map<Long, List<ArtistServiceRegion>> serviceRegions(List<Long> artistIds) {
        if (artistIds.isEmpty()) return Map.of();
        return artistProfileRepository.findServiceRegionsByArtistIds(new HashSet<>(artistIds)).stream()
                .collect(Collectors.groupingBy(r -> r.getArtistProfile().getArtistProfileId()));
    }

    private Map<Long, List<String>> artistServiceRegionLabels(List<Long> artistIds) {
        if (artistIds.isEmpty()) return Map.of();
        return artistProfileRepository.findArtistServiceRegionRows(new ArrayList<>(new LinkedHashSet<>(artistIds))).stream()
                .collect(Collectors.groupingBy(SearchServiceRegionRow::artistProfileId,
                        Collectors.collectingAndThen(Collectors.mapping(r -> regionLabel(r.region1DepthName(), r.region2DepthName()),
                                Collectors.toList()), l -> l.stream().distinct().limit(5).toList())));
    }

    private Map<Long, List<String>> artistInstruments(List<Long> artistIds) {
        if (artistIds.isEmpty()) return Map.of();
        return artistProfileRepository.findArtistInstrumentRows(artistIds).stream()
                .collect(Collectors.groupingBy(ArtistInstrumentRow::artistProfileId,
                        Collectors.collectingAndThen(Collectors.mapping(ArtistInstrumentRow::instName, Collectors.toList()),
                                l -> l.stream().distinct().toList())));
    }

    private static Map<String, Object> reviewSummary(ReviewSummaryResponse s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", s.reviewCount());
        m.put("average_rating", s.reviewCount() == 0 ? null : s.averageRating());
        m.put("top_keywords", s.topKeywords().stream().limit(3).map(k -> k.keywordName()).toList());
        return m;
    }

    private List<Long> resolveInstruments(List<String> names, List<String> notes) {
        if (names == null || names.isEmpty()) return List.of();
        List<Instrument> all = instrumentRepository.findAllByStatusOrderByInstNameAsc(InstrumentStatus.APPROVED);
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String raw : names.stream().limit(10).toList()) {
            String n = norm(raw);
            if (n.isEmpty()) continue;
            List<Instrument> exact = all.stream().filter(i -> norm(i.getInstName()).equals(n)).toList();
            List<Instrument> hit = !exact.isEmpty() ? exact
                    : all.stream().filter(i -> norm(i.getInstName()).contains(n) || n.contains(norm(i.getInstName()))).limit(5).toList();
            if (hit.isEmpty()) notes.add("악기 '" + AiText.clean(raw, 20) + "' 은(는) MU:ZIN 악기 목록에 없습니다.");
            hit.forEach(i -> ids.add(i.getInstId()));
        }
        return new ArrayList<>(ids);
    }

    private List<String> availableInstrumentNames() {
        return instrumentRepository.findAllByStatusOrderByInstNameAsc(InstrumentStatus.APPROVED).stream()
                .map(Instrument::getInstName).limit(60).toList();
    }

    private List<Long> resolveStyleTags(List<String> names, List<String> notes) {
        if (names == null || names.isEmpty()) return List.of();
        List<LessonStyleTag> all = lessonStyleTagRepository.findAllByOrderByStyleNameAsc();
        List<Long> ids = new ArrayList<>();
        for (String raw : names.stream().limit(5).toList()) {
            String n = norm(raw);
            all.stream().filter(t -> norm(t.getStyleName()).equals(n) || norm(t.getStyleName()).contains(n))
                    .findFirst().ifPresentOrElse(t -> ids.add(t.getLessonStyleTagId()),
                            () -> notes.add("스타일 '" + AiText.clean(raw, 20) + "' 은(는) 등록된 레슨 스타일이 아닙니다. 사용 가능: "
                                    + all.stream().map(LessonStyleTag::getStyleName).collect(Collectors.joining(", "))));
        }
        return ids;
    }

    // "서울" -> "서울특별시", "강남" -> "강남구" (행정구역 이름과 정확히 일치해야 검색되므로)
    private String[] resolveRegion(String r1, String r2, List<String> notes) {
        String in1 = trimTo(r1, 20);
        if (in1 == null) return new String[]{null, null};
        List<RegionOptionDto> sido = regionService.getSidoList(null);
        Optional<RegionOptionDto> s = sido.stream().filter(o -> o.name().equals(in1)).findFirst()
                .or(() -> sido.stream().filter(o -> o.name().startsWith(in1) || in1.startsWith(o.name().substring(0, 2))).findFirst());
        if (s.isEmpty()) {
            notes.add("지역 '" + AiText.clean(in1, 20) + "' 을(를) 찾지 못해 지역 조건 없이 검색했습니다.");
            return new String[]{null, null};
        }
        String in2 = trimTo(r2, 20);
        if (in2 == null) return new String[]{s.get().name(), null};
        List<RegionOptionDto> children = regionService.getChildRegions(s.get().regionId(), null);
        Optional<RegionOptionDto> c = children.stream().filter(o -> o.name().equals(in2)).findFirst()
                .or(() -> children.stream().filter(o -> o.name().startsWith(in2)).findFirst());
        if (c.isEmpty()) {
            notes.add("'" + AiText.clean(in2, 20) + "' 을(를) " + s.get().name() + " 안에서 찾지 못해 시·도 기준으로 검색했습니다.");
            return new String[]{s.get().name(), null};
        }
        return new String[]{s.get().name(), c.get().name()};
    }

    private static Map<String, Object> appliedFilters(LessonQuery q, String[] region, List<Long> instIds, List<Long> tagIds) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!instIds.isEmpty()) m.put("instrument_ids", instIds);
        if (region[0] != null) m.put("region", regionLabel(region[0], region[1]));
        if (q.mode() != null) m.put("mode", q.mode());
        if (q.maxPrice() != null) m.put("max_price_krw", q.maxPrice());
        if (q.daysOfWeek() != null && !q.daysOfWeek().isEmpty()) m.put("days_of_week", q.daysOfWeek());
        if (q.timeParts() != null && !q.timeParts().isEmpty()) m.put("time_parts", q.timeParts());
        if (q.keyword() != null && !q.keyword().isBlank()) m.put("keyword", AiText.clean(q.keyword(), 40));
        if (!tagIds.isEmpty()) m.put("style_tag_ids", tagIds);
        return m;
    }

    static String regionLabel(String r1, String r2) {
        if (r1 == null || r1.isBlank()) return null;
        return r2 == null || r2.isBlank() ? r1 : r1 + " " + r2;
    }

    private static String untrusted(String text, int max) {
        return AiText.clean(text, max);
    }

    private static String norm(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String trimTo(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
