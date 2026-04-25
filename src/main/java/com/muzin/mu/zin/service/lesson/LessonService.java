package com.muzin.mu.zin.service.lesson;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.artist.ArtistSummaryResponse;
import com.muzin.mu.zin.dto.lesson.*;
import com.muzin.mu.zin.dto.region.SearchMainRegionSummary;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.TimePart;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.instrument.Instrument;
import com.muzin.mu.zin.entity.instrument.InstrumentCategory;
import com.muzin.mu.zin.entity.lesson.*;
import com.muzin.mu.zin.repository.ArtistInstrumentRepository;
import com.muzin.mu.zin.repository.artist.ArtistProfileRepository;
import com.muzin.mu.zin.repository.InstrumentRepository;
import com.muzin.mu.zin.repository.lesson.*;
import com.muzin.mu.zin.security.model.PrincipalUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonRepository lessonRepository;
    private final LessonRepositoryCustom lessonRepositoryCustom;
    private final LessonStyleMapRepository lessonStyleMapRepository;
    private final LessonTimeSlotRepository lessonTimeSlotRepository;
    private final ArtistProfileRepository artistProfileRepository;
    private final ArtistInstrumentRepository artistInstrumentRepository;
    private final InstrumentRepository instrumentRepository;

    // 새 레슨 만들기
    @Transactional
    public ApiRespDto<LessonCreateResponse> createLesson(LessonCreateRequest req, PrincipalUser principalUser) {

        ArtistProfile profile = artistProfileRepository.findByUser_UserId(principalUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("아티스트 프로필이 없습니다."));

        // 내 악기인지 검증
        boolean ok = artistInstrumentRepository
                .existsByArtistProfile_ArtistProfileIdAndInstrument_InstId(
                        profile.getArtistProfileId(), req.instId()
                );
        if (!ok) throw new IllegalArgumentException("등록한 악기만 레슨에 설정 가능합니다.");

        Instrument instrument = instrumentRepository.findById(req.instId())
                .orElseThrow(() -> new IllegalArgumentException("악기가 없습니다."));

        LessonClosingPolicy closingPolicy = req.closingPolicy() != null
                        ? req.closingPolicy()
                        : LessonClosingPolicy.KEEP_OPEN_FOR_REQUEST;

        Lesson lesson = Lesson.builder()
                .artistProfile(profile)
                .title(req.title())
                .instrument(instrument)
                .mode(req.mode())
                .durationMin(req.durationMin())
                .price(req.price())
                .description(req.description())
                .requirementText(req.requirementText())
                .closingPolicy(closingPolicy)
                .build();

        Lesson saved = lessonRepository.save(lesson);

        return new ApiRespDto<>("success", "레슨이 생성되었습니다.", new LessonCreateResponse(
                saved.getLessonId(), saved.getTitle(), saved.getMode(), saved.getStatus(), saved.getClosingPolicy()));

    }

    // 아티스트 본인 레슨인지 검증 + 수정
    @Transactional
    public ApiRespDto<?> updateLesson(Long lessonId, LessonUpdateRequest req, PrincipalUser principalUser) {

        ArtistProfile profile = artistProfileRepository.findByUser_UserId(principalUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("아티스트 프로필이 없습니다."));

        Lesson lesson = lessonRepository.findByLessonIdAndArtistProfile_ArtistProfileId(lessonId, profile.getArtistProfileId())
                .orElseThrow(() -> new IllegalArgumentException("레슨이 없거나 권한이 없습니다."));

        if (lesson.isDeleted()) {
            throw new IllegalArgumentException("삭제된 레슨을 수정할 수 없습니다.");
        }

        // 예약이 하나라도 있는지 체크(예약 되어있으면 수정 불가능하게 해야하는 부분이 있어서)
        boolean hasBookedSlot = lessonTimeSlotRepository.existsByLesson_LessonIdAndStatus(
                lessonId,
                TimeSlotStatus.BOOKED);

        if (hasBookedSlot) {
            boolean tryingToChangeCore =
                    req.title() != null ||
                    req.mode() != null ||
                    req.price() != null ||
                    req.durationMin() != null ||
                    req.instId() != null;

            if (tryingToChangeCore) {
                throw new IllegalArgumentException("예약된 슬롯이 있는 레슨은 수정이 불가능합니다.");
            }
        }

        // 악기 변경(예약 없을 때만 여기까지 옴)
        if (req.instId() != null ) {

            boolean allowed = artistInstrumentRepository
                    .existsByArtistProfile_ArtistProfileIdAndInstrument_InstId(profile.getArtistProfileId(), req.instId());

            if (!allowed) {
                throw new IllegalArgumentException("내 프로필에 등록한 악기만 레슨 악기로 선택가능합니다.");
            }

            Instrument inst = instrumentRepository.findById(req.instId())
                    .orElseThrow(() -> new IllegalArgumentException("악기가 존재하지 않습니다."));

            lesson.changeInstrument(inst);
        }

        lesson.applyUpdate(
                req.title(),
                req.mode(),
                req.description(),
                req.requirementText(),
                req.price(),
                req.durationMin(),
                req.closingPolicy());

        // 일단 status도 같이 바꿔줌
        if (req.status() != null) {
            lesson.changeStatus(req.status());
        }

        // 스타일 태그도 같이 내려주기
        List<LessonStyleTagResponse> styleTags = loadStyleTags(lesson.getLessonId());

        ArtistLessonResponse resp = new ArtistLessonResponse(
                lesson.getLessonId(),
                lesson.getTitle(),
                lesson.getInstrument().getInstId(),
                lesson.getDescription(),
                lesson.getRequirementText(),
                lesson.getPrice(),
                lesson.getDurationMin(),
                lesson.getMode(),
                lesson.getStatus(),
                styleTags,
                lesson.getClosingPolicy(),
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

        // 예약(BOOKED) 타임슬롯 있으면 삭제 금지(비활성은 가능함)
        boolean hasBookedSlot = lessonTimeSlotRepository.existsByLesson_LessonIdAndStatus(
                lessonId, TimeSlotStatus.BOOKED
        );

        if (hasBookedSlot) {
            throw new IllegalArgumentException("예약된 시간이 있는 레슨은 삭제할 수 없습니다.");
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
                    l.getInstrument().getInstId(),
                    l.getDescription(),
                    l.getRequirementText(),
                    l.getPrice(),
                    l.getDurationMin(),
                    l.getMode(),
                    l.getStatus(),
                    styleTags,
                    l.getClosingPolicy(),
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
        // 이것때문에 레슨 삭제하고 리스트 화면으로 돌아왔을때 오류나서 코드 보류
//        if (lesson.isDeleted()) {
//            throw new IllegalArgumentException("삭제된 레슨입니다.");
//        }

        // 이거 api를 따로 빼고 구현해서 계속 이래야함;
//        List<LessonStyleTagResponse> styleTags = lessonStyleMapRepository
//                .findAllByLesson_LessonId(lesson.getLessonId())
//                .stream()
//                .map(m -> new LessonStyleTagResponse(
//                        m.getLessonStyleTag().getLessonStyleTagId(),
//                        m.getLessonStyleTag().getStyleName()
//                ))
//                .toList();
        List<LessonStyleTagResponse> styleTags = loadStyleTags(lesson.getLessonId());

        Long instId = lesson.getInstrument().getInstId();

        ArtistLessonResponse resp = new ArtistLessonResponse(
                lesson.getLessonId(),
                lesson.getTitle(),
                instId,
                lesson.getDescription(),
                lesson.getRequirementText(),
                lesson.getPrice(),
                lesson.getDurationMin(),
                lesson.getMode(),
                lesson.getStatus(),
                styleTags,
                lesson.getClosingPolicy(),
                lesson.getCreateDt(),
                lesson.getUpdateDt()
        );

        return new ApiRespDto<>("success","조회 완료", resp);
    }

    // 레슨 검색(레슨 리스트)
    @Transactional(readOnly = true)
    public ApiRespDto<List<LessonSearchResponse>> searchLessons(LessonSearchRequest req) {
        String keyword = (req.keyword() == null || req.keyword().isBlank() ? null : req.keyword().trim());

        LessonSort sort = (req.sort() == null) ? LessonSort.LATEST : req.sort();

        // 기본값 보정

        // 키워드 검색
        String k = (keyword == null || keyword.isBlank()) ? "" : keyword.trim();
//        boolean applyKeyword = !k.isBlank();
//
//
//        // 스타일 태그 - List는 절대 null로 넘기지 말기
//        boolean applyTags = styleTagIds != null && !styleTagIds.isEmpty();
//        List<Long> tags = applyTags ? styleTagIds : List.of(-1L);
//
//        // 악기
//        boolean applyInst = instIds != null && !instIds.isEmpty();
//        List<Long> inst = applyInst ? instIds : List.of(-1L);
//
//        // (applyTime이 false여도 from/to는 그냥 안전한 값으로 채워서 보냄)
//        // 레슨 기간
//        LocalDateTime f = (from == null) ? TimeDefaults.nowKst() : from;
//        LocalDateTime t = (to == null) ? f.plusDays(90) : to;
//
//        // 요일
//        boolean applyWeekday = (daysOfWeek != null && !daysOfWeek.isEmpty());
//        List<Integer> dows = applyWeekday ? daysOfWeek : List.of(-1);
//
//        boolean applyTimeParts = (timeParts != null && !timeParts.isEmpty());
//        List<String> parts = applyTimeParts ? timeParts : List.of("__NONE__");
//
//        // 시간 필터 정책 결정 포인트
//        // "아무 필터 없이 검색하면 전체 레슨(슬롯 없어도) 뜨게"가 목표면 applyTime=false로 둬야 함.
//        boolean applyTime = (from != null || to != null) || applyWeekday || applyTimeParts;
//
//        Pageable pageable = PageRequest.of(0, 200, toSort(sort));
        boolean useTimeFilter =
                req.from() != null || req.to() != null ||
                        (req.daysOfWeek() != null && !req.daysOfWeek().isEmpty()) ||
                        (req.timeParts() != null && !req.timeParts().isEmpty());

        LocalDateTime effectiveFrom = null;
        LocalDateTime effectiveTo = null;

        if (useTimeFilter) {
            effectiveFrom = (req.from() == null) ? TimeDefaults.nowKst() : req.from();
            effectiveTo = (req.from() == null) ? effectiveFrom.plusDays(90) : req.to();
        }

        Pageable pageable = PageRequest.of(0, 200, toSort(sort));

        LessonSearchCond cond = new LessonSearchCond(
                keyword,
                req.mode(),
                req.styleTagIds(),
                req.instCategory(),
                req.instIds(),

                req.region1DepthName(),
                req.region2DepthName(),
                req.region3DepthName(),

                effectiveFrom,
                effectiveTo,
                req.daysOfWeek(),
                req.timeParts()
        );

        List<Lesson> lessons = lessonRepositoryCustom.searchPublicLessons(cond, pageable);

        List<LessonSearchResponse> resp = lessons.stream()
                .map(l -> new LessonSearchResponse(
                        l.getLessonId(),
                        l.getTitle(),
                        l.getDescription(),
                        l.getPrice(),
                        l.getDurationMin(),
                        l.getMode(),
                        l.getStatus(),

                        l.getInstrument().getInstId(),
                        l.getInstrument().getInstName(),
                        l.getInstrument().getCategory(),

                        new SearchMainRegionSummary(
                                l.getArtistProfile().getRegion1DepthName(),
                                l.getArtistProfile().getRegion2DepthName(),
                                l.getArtistProfile().getRegion3DepthName(),
                                l.getArtistProfile().getAddressLabel()
                        )
                ))
                .toList();

        return new ApiRespDto<>("success", "", resp);
    };

    // 레슨 디테일 단일 조회
    @Transactional(readOnly = true)
    public ApiRespDto<LessonDetailResponse> getPublicLessonDetail(Long lessonId) {

        Lesson lesson = lessonRepositoryCustom.findPublicDetailByIdDsl(lessonId)
                .orElseThrow(() -> new IllegalArgumentException("레슨이 없습니다."));

        if (lesson.isDeleted()) {
            throw new IllegalArgumentException("삭제된 레슨입니다.");
        }

        // 비활성 레슨 숨기기
        if (lesson.getStatus() != LessonStatus.ACTIVE) {
            throw new IllegalArgumentException("비활성 레슨입니다.");
        }

        List<LessonStyleTagResponse> styleTags = loadStyleTags(lesson.getLessonId());

        ArtistProfile profile = lesson.getArtistProfile();
        User user = profile.getUser();

        ArtistSummaryResponse artist = new ArtistSummaryResponse(
                profile.getArtistProfileId(),
                user.getUsername(),
                user.getProfileImgUrl()
        );

        Long instId = lesson.getInstrument().getInstId();

        LessonDetailResponse resp = new LessonDetailResponse(
                lesson.getLessonId(),
                lesson.getTitle(),
                instId,
                lesson.getDescription(),
                lesson.getRequirementText(),
                lesson.getPrice(),
                lesson.getDurationMin(),
                lesson.getMode(),
                lesson.getStatus(),
                styleTags,
                lesson.getCreateDt(),
                lesson.getUpdateDt(),
                artist
        );

        return new ApiRespDto<>("success", "조회 완료", resp);
    }




    // 스타일 태그 유틸
    private List<LessonStyleTagResponse> loadStyleTags(Long lessonId) {
        return lessonStyleMapRepository.findAllByLesson_LessonId(lessonId).stream()
                .map(m -> new LessonStyleTagResponse(
                        m.getLessonStyleTag().getLessonStyleTagId(),
                        m.getLessonStyleTag().getStyleName()
                ))
                .toList();
    }

    // Sort 매핑
    private Sort toSort(LessonSort sort) {
        return switch (sort) {
            case LATEST -> Sort.by(Sort.Order.desc("updateDt"));
            case OLDEST -> Sort.by(Sort.Order.asc("createDt"));
            case PRICE_ASC -> Sort.by(Sort.Order.asc("price").nullsLast())
                    .and(Sort.by(Sort.Order.desc("updateDt")));
            case PRICE_DESC -> Sort.by(Sort.Order.desc("price").nullsLast())
                    .and(Sort.by(Sort.Order.desc("updateDt")));
        };
    }

}
