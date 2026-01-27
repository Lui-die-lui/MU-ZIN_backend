package com.muzin.mu.zin.service.lesson.recurrence;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.LessonRecurrenceResponse;
import com.muzin.mu.zin.dto.lesson.LessonRecurrenceUpsertRequest;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.lesson.LessonRecurrenceRule;
import com.muzin.mu.zin.entity.lesson.LessonTimeSlot;
import com.muzin.mu.zin.entity.lesson.TimeSlotStatus;
import com.muzin.mu.zin.policy.TimeSlotPolicy;
import com.muzin.mu.zin.repository.ArtistProfileRepository;
import com.muzin.mu.zin.repository.lesson.LessonRecurrenceRuleRepository;
import com.muzin.mu.zin.repository.lesson.LessonRepository;
import com.muzin.mu.zin.repository.lesson.LessonTimeSlotRepository;
import com.muzin.mu.zin.security.model.PrincipalUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LessonRecurrenceService {

    private final LessonRepository lessonRepository;
    private final LessonTimeSlotRepository lessonTimeSlotRepository;
    private final ArtistProfileRepository artistProfileRepository;
    private final LessonRecurrenceRuleRepository ruleRepository;

    @Transactional(readOnly = true)
    public ApiRespDto<LessonRecurrenceResponse> getRule(Long lessonId, PrincipalUser principalUser) {
        getMyLessonOrThrow(lessonId, principalUser); // 소유권 및 존재 검증만

        // 반복 규칙이 존재하지 않을 경우 에러 x / 검증 정도만
        LessonRecurrenceRule rule = ruleRepository.findByLesson_LessonId(lessonId).orElse(null);

        if (rule == null) {
            return new ApiRespDto<>("success","RECURRENCE_NOT_SET",null);
        }

        return new ApiRespDto<>("success", "조회 완료", toResponse(rule, lessonId));
    }

    @Transactional
    public ApiRespDto<LessonRecurrenceResponse> upsertRuleAndMaterialize(
            Long lessonId,
            LessonRecurrenceUpsertRequest req,
            PrincipalUser principalUser
    ) {
        Lesson lesson = getMyLessonOrThrow(lessonId, principalUser);

        // 기본값 / 검증
        boolean enabled = (req.enabled() == null) ? true : req.enabled();

        // default 시간대(한국으로 고정)
        String timezone = TimeDefaults.DEFAULT_TZ;
        if (req.timezone() != null && !req.timezone().isBlank()
            && !req.timezone().trim().equals(TimeDefaults.DEFAULT_TZ)) {
            throw new IllegalArgumentException("해당 서비스는 한국 시간대만 지원합니다.");
        }

        int mask = (req.daysOfWeekMask() == null)
                ? RecurrenceDefaults.DEFAULT_DOW_MASK
                : req.daysOfWeekMask();

        LocalTime startTime = Objects.requireNonNull(req.startTime(), "시작 시간은 필수입니다.");
        LocalTime endTime = Objects.requireNonNull(req.endTime(), "수업 종료 시간은 필수입니다.");
        int intervalMin = Objects.requireNonNull(req.intervalMin(), "수업 간격 입력은 필수입니다.");

        // 입력하지 않으면 상수에 입력해둔 기본값으로 (6주 반복)
        int weeksAhead = (req.weeksAhead() == null)
                ? RecurrenceDefaults.DEFAULT_WEEKS_AHEAD
                : req.weeksAhead();

        // 시간 검증
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("수업 종료 시간은 시작 시간보다 이후여야 합니다.");
        }

        int durationMin = lesson.getDurationMin();
        if (intervalMin < durationMin) { // 쉬는시간이 음수거나 수업 중 다음 수업이 시작되는 케이스 막아줌
            throw new IllegalArgumentException("intervalMin은 수업시간 이상이어야합니다.");
        }

        // 반복 최대 설정
        if (weeksAhead < 1 || weeksAhead > RecurrenceDefaults.MAX_WEEKS_AHEAD) {
            throw new IllegalArgumentException("반복할 주간은 1 ~ 13 사이로 설정하세요.");
        }

        if (mask <= 0) {
            throw new IllegalArgumentException("요일 마스크가 올바르지 않습니다.");
        }

        // upsert(수정 혹은 생성)
        LessonRecurrenceRule rule = ruleRepository.findByLesson_LessonId(lessonId)
                .orElseGet(() -> LessonRecurrenceRule.builder()
                        .lesson(lesson)
                        .materializedUntil(null)
                        .build());

        rule.applyUpdate(enabled, timezone, mask, startTime, endTime, intervalMin, weeksAhead);

        LessonRecurrenceRule saved = ruleRepository.save(rule);

        if (enabled) {
            materializeSlots(lesson, saved);
        }

        return new ApiRespDto<>("success", "반복 규칙 저장 완료", toResponse(saved, lessonId));
    }

    private  void materializeSlots(Lesson lesson, LessonRecurrenceRule rule) {

        ZoneId zone = TimeDefaults.DEFAULT_ZONE;

        LocalDateTime now = LocalDateTime.now(zone);

        int durationMin = lesson.getDurationMin();
        int intervalMin = rule.getIntervalMin();

        LocalDate startDate = LocalDate.now(zone);
        LocalDate targetUntil = startDate.plusWeeks(rule.getWeeksAhead());

        // 90일 상한 캡핑
        LocalDate maxUntil = startDate.plusDays(TimeSlotPolicy.MAX_DAYS_AHEAD);
        if (targetUntil.isAfter(maxUntil)) targetUntil = maxUntil;

        LocalDate fromDate = (rule.getMaterializedUntil() == null)
                ? startDate
                : rule.getMaterializedUntil().plusDays(1);

        if (fromDate.isAfter(targetUntil)) return;

        List<LessonTimeSlot> toSave = new ArrayList<>();

        LocalDateTime upperExclusive = TimeSlotPolicy.upperExclusive(now);

        // for + while 문으로 돌리기
        for (LocalDate d = fromDate; !d.isAfter(targetUntil); d = d.plusDays(1)) {
            if (!matchesDayMask(d.getDayOfWeek(), rule.getDaysOfWeekMask())) continue;

            LocalTime t = rule.getStartTime();
            while (true) {
                LocalDateTime startDt = LocalDateTime.of(d, t);
                LocalDateTime endDt = startDt.plusMinutes(durationMin);

                if (endDt.toLocalTime().isAfter(rule.getEndTime()) || endDt.toLocalDate().isAfter(d)) {
                    break;
                }

                if (!startDt.isAfter(now)) {
                    t = t.plusMinutes(intervalMin);
                    if (!t.isBefore(rule.getEndTime())) break;
                    continue;
                }

                if (!startDt.isBefore(upperExclusive)) break;

                boolean exists = lessonTimeSlotRepository.existsByLesson_LessonIdAndStartDt(lesson.getLessonId(), startDt);
                if (!exists) {
                    toSave.add(LessonTimeSlot.builder()
                                    .lesson(lesson)
                                    .startDt(startDt)
                                    .endDt(endDt)
                                    .status(TimeSlotStatus.OPEN)
                                    .build());
                }

                t = t.plusMinutes(intervalMin);
                if (!t.isBefore(rule.getEndTime())) break;
            }
        }

        if (!toSave.isEmpty()) { // db 호출을 최소화 - 비어있지 않을때만 일어남
            int maxPerLesson = 400;
            int existingCount = lessonTimeSlotRepository.countByLesson_LessonId(lesson.getLessonId());

            if (existingCount + toSave.size() > maxPerLesson) {
                throw new IllegalArgumentException("레슨당 타임슬롯은 최대 " + maxPerLesson + "개까지 생성할 수 있습니다.");
            }
                try {
                    lessonTimeSlotRepository.saveAll(toSave);
                } catch (DataIntegrityViolationException e) {
                    throw new IllegalArgumentException("슬롯 생성중 중복 시간이 포함되어있습니다.");
                }
        }

        rule.setMaterializedUntil(targetUntil);
        ruleRepository.save(rule);
    }
    // 요일 mask 체크
    private boolean matchesDayMask(DayOfWeek dow, int mask) {
        int bit = switch (dow) {
            case MONDAY -> 1;
            case TUESDAY -> 2;
            case WEDNESDAY -> 4;
            case THURSDAY -> 8;
            case FRIDAY -> 16;
            case SATURDAY -> 32;
            case SUNDAY -> 64;
        };
        return (mask & bit) != 0; // 그래서 월 - 일 다 더하면 127이라고...
    }

    // 공통 응답 값
    private LessonRecurrenceResponse toResponse(LessonRecurrenceRule rule, Long lessonId) {
        return new LessonRecurrenceResponse(
                rule.getRuleId(),
                lessonId,
                rule.getEnabled(),
                rule.getTimezone(),
                rule.getDaysOfWeekMask(),
                rule.getStartTime(),
                rule.getEndTime(),
                rule.getIntervalMin(),
                rule.getWeeksAhead(),
                rule.getMaterializedUntil()
        );
    }

    // 레슨 소유권 체크 - 이거 계속 반복되는 로직이라 나중에 리팩토링 상황에서 고려해보기
    private ArtistProfile getMyArtistProfileOrThrow(PrincipalUser principalUser) {
        return artistProfileRepository.findByUser_UserId(principalUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("아티스트 프로필이 없습니다."));
    }

    private Lesson getMyLessonOrThrow(Long lessonId, PrincipalUser principalUser) {
        ArtistProfile profile = getMyArtistProfileOrThrow(principalUser);
        return lessonRepository.findByLessonIdAndArtistProfile_ArtistProfileId(lessonId, profile.getArtistProfileId())
                .orElseThrow(() -> new IllegalArgumentException("레슨이 없거나 권한이 없습니다."));
    }
}
