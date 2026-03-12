package com.muzin.mu.zin.service.lesson;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.TimeSlotCreateRequest;
import com.muzin.mu.zin.dto.lesson.TimeSlotResponse;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.TimePart;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.lesson.LessonStatus;
import com.muzin.mu.zin.entity.lesson.LessonTimeSlot;
import com.muzin.mu.zin.entity.lesson.TimeSlotStatus;
import com.muzin.mu.zin.policy.TimeSlotPolicy;
import com.muzin.mu.zin.repository.artist.ArtistProfileRepository;
import com.muzin.mu.zin.repository.lesson.LessonRepository;
import com.muzin.mu.zin.repository.lesson.LessonTimeSlotRepository;
import com.muzin.mu.zin.security.model.PrincipalUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LessonTimeSlotService {

    private final LessonRepository lessonRepository;
    private final LessonTimeSlotRepository lessonTimeSlotRepository;
    private final ArtistProfileRepository artistProfileRepository;

    // 유저용 OPEN 슬롯 조회
    @Transactional(readOnly = true)
    public ApiRespDto<List<TimeSlotResponse>> getOpenSlots(
            Long lessonId,
            LocalDateTime from, LocalDateTime to,
            List<Integer> daysOfWeek,
            List<TimePart> timeParts
    ) {

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new IllegalArgumentException("레슨이 없습니다."));

        // 빈배열 말고 예외 던지는중
        if (lesson.isDeleted()) {
            throw new IllegalArgumentException("삭제된 레슨입니다.");
        }

        // 비활성 레슨은 슬롯 조회/예약 흐름 차단
        if (lesson.getStatus() != LessonStatus.ACTIVE) {
            throw new IllegalArgumentException("비활성 레슨입니다.");
        }

        // 기본값 / 보정
        LocalDateTime now = TimeDefaults.nowKst();
        LocalDateTime f = (from == null) ? now : (from.isBefore(now) ? now : from);
        LocalDateTime t = (to == null) ? f.plusDays(90) : to;

        // from > to 방어
        if (t.isBefore(f)) {
            throw new IllegalArgumentException("to는 from 이후여야 합니다.");
        }

        List<LessonTimeSlot> slots = lessonTimeSlotRepository
                .findAllByLesson_LessonIdAndStartDtBetweenOrderByStartDtAsc(lessonId, f, t);

        boolean applyDays = daysOfWeek != null && !daysOfWeek.isEmpty();
        boolean applyParts = timeParts != null && !timeParts.isEmpty();

        List<TimeSlotResponse> resp = slots.stream()
                .filter(s -> s.getStatus() == TimeSlotStatus.OPEN)
                .filter(s-> !s.getStartDt().isBefore(LocalDateTime.now())) // 과거시간 조회 막음
                .filter(s -> !applyDays || daysOfWeek.contains(toIsoDow(s.getStartDt())))
                .filter(s -> !applyParts || timeParts.contains(toTimePart(s.getStartDt())))
                .map(this::toResponse)
                .toList();

        return new ApiRespDto<>("success","",resp);
    }


    // 아티스트용 내 레슨 시간 슬롯 전체 조회
    @Transactional(readOnly = true)
    public ApiRespDto<List<TimeSlotResponse>> getArtistSlots(Long lessonId, LocalDateTime from, LocalDateTime to, PrincipalUser principalUser) {

        Lesson lesson = getMyLessonOrThrow(lessonId, principalUser);

        if (lesson.isDeleted()) {
            return new ApiRespDto<>("success", "", List.of());
        }


        List<LessonTimeSlot> slots = lessonTimeSlotRepository
                .findAllByLesson_LessonIdAndStartDtBetweenOrderByStartDtAsc(lessonId, from, to);

        List<TimeSlotResponse> resp = slots.stream()
                .map(this::toResponse) // 아티스트 전체 상태 반환
                .toList();

        return new ApiRespDto<>("success", "", resp);
    }


    // 아티스트용 슬롯 생성
    @Transactional
    public ApiRespDto<List<TimeSlotResponse>> createSlots(Long lessonId, TimeSlotCreateRequest req, PrincipalUser principalUser) {

        Lesson lesson = getMyLessonOrThrow(lessonId, principalUser);

        if (lesson.isDeleted()) {
            throw new IllegalArgumentException("삭제된 레슨입니다.");
        }

        if (req.startDts() == null || req.startDts().isEmpty()) {
            throw new IllegalArgumentException("등록할 시간이 없습니다.");
        }

        // 입력되는 진행 시간
        int duration = lesson.getDurationMin();
        LocalDateTime now = LocalDateTime.now(TimeDefaults.DEFAULT_ZONE);

        // 요청 내 중복 및 null 제거
        List<LocalDateTime> unique = req.startDts()
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 과거/현재 + 오늘 기준 90일 상한 검증
        // 기존 invalid 리스트 만들 필요 없이 policy로 통일
        TimeSlotPolicy.validateStartDts(unique, now);

//        List<LocalDateTime> invalid = unique.stream()
//                .filter(start -> !start.isAfter(now)) // now이하 (과거 + 현재) 금지
//                .toList();
//
//        if (!invalid.isEmpty()) {
//            throw new IllegalArgumentException("현재 및 과거 시간 슬롯은 생성 불가능합니다.");
//        }

        // 레슨당 슬롯 총량 상한
        int maxPerLesson = 400;
        int existingCount = lessonTimeSlotRepository.countByLesson_LessonId(lessonId);

        // 지금 요청이 모두 신규로 저장되는건 아니니,
        // 실제 저장될 후보만 센다.
        long willCreate = unique.stream()
                .filter(start -> !lessonTimeSlotRepository.existsByLesson_LessonIdAndStartDt(lessonId, start))
                .count();

        if (existingCount + willCreate > maxPerLesson) {
            throw new IllegalArgumentException("레슨당 타임슬롯은 최대 " + maxPerLesson + "개까지 생성할 수 있습니다.");
        }

        List<LessonTimeSlot> toSave = unique.stream()
                // DB 중복 방지
                .filter(start -> !lessonTimeSlotRepository.existsByLesson_LessonIdAndStartDt(lessonId, start))
                .map(start -> LessonTimeSlot.builder()
                        .lesson(lesson)
                        .startDt(start)
                        .endDt(start.plusMinutes(duration)) // 저장되는 진짜 endDt
                        .status(TimeSlotStatus.OPEN)
                        .build())
                .toList();

        // 유니크 충돌 예외 잡기
        try {
            List<LessonTimeSlot> saved = lessonTimeSlotRepository.saveAll(toSave);

            List<TimeSlotResponse> resp = saved.stream()
                    .map(this::toResponse)
                    .toList();

            return new ApiRespDto<>("success", "", resp);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("이미 등록된 시간이 포함되어 있습니다.");
        }
    }


    // 아티스트 슬롯 닫기/열기
    @Transactional
    public ApiRespDto<?> closeSlot(Long timeSlotId, PrincipalUser principalUser) {

        LessonTimeSlot slot = getArtistSlotOrThrow(timeSlotId, principalUser);

        if (slot.getStatus() == TimeSlotStatus.BOOKED) {
            throw new IllegalArgumentException("예약된 슬롯은 닫을 수 없습니다.");
        }
        slot.close();
        return new ApiRespDto<>("success","슬롯이 닫혔습니다.",null);

    }

    @Transactional
    public ApiRespDto<?> openSlot(Long timeSlotId, PrincipalUser principalUser) {

        LessonTimeSlot slot = getArtistSlotOrThrow(timeSlotId, principalUser);

        if (slot.getStatus() == TimeSlotStatus.BOOKED) {
            throw new IllegalArgumentException("예약된 슬롯은 열 수 없습니다.");
        }
        slot.open();
        return new ApiRespDto<>("success", "슬롯이 열렸습니다.", null);
    }

    // 아티스트 타임 슬롯 삭제
    @Transactional
    public ApiRespDto<?> deleteSlot(Long timeSlotId, PrincipalUser principalUser) {

        LessonTimeSlot slot = getArtistSlotOrThrow(timeSlotId, principalUser);

        if (slot.getStatus() == TimeSlotStatus.BOOKED) {
            throw new IllegalArgumentException("예약된 슬롯은 삭제할 수 없습니다.");
        }

        lessonTimeSlotRepository.delete(slot);
        return new ApiRespDto<>("success", "슬롯이 삭제되었습니다.", null);

    }


    // 공통 유틸

    // 본인 아티스트 프로필이 있는지 검증
    private ArtistProfile getMyArtistProfileOrThrow(PrincipalUser principalUser) {
        return artistProfileRepository.findByUser_UserId(principalUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("아티스트 프로필이 없습니다."));
    }

    // 본인 레슨이 존재 하는지 검증
    private Lesson getMyLessonOrThrow(Long lessonId, PrincipalUser principalUser) {
        ArtistProfile profile = getMyArtistProfileOrThrow(principalUser);
        return lessonRepository.findByLessonIdAndArtistProfile_ArtistProfileId(lessonId, profile.getArtistProfileId())
                .orElseThrow(() -> new IllegalArgumentException("레슨이 없거나 권한이 없습니다."));
    }




    // 본인 타임 슬롯이 맞는지 검증
    private LessonTimeSlot getArtistSlotOrThrow(Long timeSlotId, PrincipalUser principalUser) {
        ArtistProfile profile = getMyArtistProfileOrThrow(principalUser);

        return lessonTimeSlotRepository
                .findByTimeSlotIdAndLesson_ArtistProfile_ArtistProfileId(timeSlotId, profile.getArtistProfileId())
                .orElseThrow(() -> new IllegalArgumentException("슬롯이 없거나 권한이 없습니다."));
    }

    private TimeSlotResponse toResponse(LessonTimeSlot slot) {
//        LocalDateTime start = slot.getStartDt();
        return new TimeSlotResponse(
                slot.getTimeSlotId(),
                slot.getStartDt(),
                slot.getEndDt(),
                slot.getStatus()
        );
    }

    private int toIsoDow(LocalDateTime dt) {
        // Java DayOfWeek: MONDAY=1 ... SUNDAY=7
        return dt.getDayOfWeek().getValue();
    }

    private TimePart toTimePart(LocalDateTime dt) {
        int h = dt.getHour();
        // 너가 정한 기준대로 맞추면 됨
        if (h >= 6 && h <= 11) return TimePart.MORNING;
        if (h >= 12 && h <= 17) return TimePart.AFTERNOON;
        if (h >= 18 && h <= 23) return TimePart.EVENING;
        return TimePart.DAWN; // 0~5
    }
}
