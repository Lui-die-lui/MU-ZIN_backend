package com.muzin.mu.zin.service.lesson;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.lesson.TimeSlotCreateRequest;
import com.muzin.mu.zin.dto.lesson.TimeSlotResponse;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.lesson.LessonStatus;
import com.muzin.mu.zin.entity.lesson.LessonTimeSlot;
import com.muzin.mu.zin.entity.lesson.TimeSlotStatus;
import com.muzin.mu.zin.repository.ArtistProfileRepository;
import com.muzin.mu.zin.repository.lesson.LessonRepository;
import com.muzin.mu.zin.repository.lesson.LessonTimeSlotRepository;
import com.muzin.mu.zin.security.model.PrincipalUser;
import lombok.RequiredArgsConstructor;
import org.hibernate.boot.model.naming.IllegalIdentifierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LessonTimeSlotService {

    private final LessonRepository lessonRepository;
    private final LessonTimeSlotRepository lessonTimeSlotRepository;
    private final ArtistProfileRepository artistProfileRepository;

    // 유저용 OPEN 슬롯 조회
    public ApiRespDto<List<TimeSlotResponse>> getOpenSlot(Long lessonId, LocalDateTime from, LocalDateTime to) {

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new IllegalArgumentException("레슨이 없습니다."));

        if (lesson.isDeleted()) {
//                return new ApiRespDto<>("success","",List.of());
            throw new IllegalArgumentException("삭제된 레슨입니다.");
        }

        // 레슨 진행 시간
        int duration = lesson.getDurationMin();

        List<LessonTimeSlot> slots = lessonTimeSlotRepository
                .findAllByLesson_LessonIdAndStartDtBetweenOrderByStartDtAsc(lessonId, from, to);

        List<TimeSlotResponse> resp = slots.stream()
                .filter(s -> s.getStatus() == TimeSlotStatus.OPEN)
                .map(s -> toResponse(s, duration))
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

        int duration = lesson.getDurationMin();

        List<LessonTimeSlot> slots = lessonTimeSlotRepository
                .findAllByLesson_LessonIdAndStartDtBetweenOrderByStartDtAsc(lessonId, from, to);

        List<TimeSlotResponse> resp = slots.stream()
                .map(s -> toResponse(s, duration)) // 아티스트 전체 상태 반환
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

        // 요청 내 중복 제거
        List<LocalDateTime> unique = req.startDts().stream().distinct().toList();

        List<LessonTimeSlot> toSave = unique.stream()
                // 과거 시간 방지
                .filter(start -> !start.isBefore(LocalDateTime.now()))
                // DB 중복 방지
                .filter(start -> !lessonTimeSlotRepository.existsByLesson_LessonIdAndStartDt(lessonId, start))
                .map(start -> LessonTimeSlot.builder()
                        .lesson(lesson)
                        .startDt(start)
                        .status(TimeSlotStatus.OPEN)
                        .build())
                .toList();

        List<LessonTimeSlot> saved = lessonTimeSlotRepository.saveAll(toSave);

        int duration = lesson.getDurationMin();
        List<TimeSlotResponse> resp = saved.stream()
                .map(s -> toResponse(s, duration))
                .toList();

        return new ApiRespDto<>("success", "", resp);
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

    private TimeSlotResponse toResponse(LessonTimeSlot slot, int durationMin) {
        LocalDateTime start = slot.getStartDt();
        return new TimeSlotResponse(
                slot.getTimeSlotId(),
                start,
                start.plusMinutes(durationMin),
                slot.getStatus()
        );
    }
}
