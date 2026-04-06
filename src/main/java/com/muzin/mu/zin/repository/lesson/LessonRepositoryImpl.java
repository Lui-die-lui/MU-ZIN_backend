package com.muzin.mu.zin.repository.lesson;

import com.muzin.mu.zin.dto.lesson.LessonCardRow;
import com.muzin.mu.zin.entity.QArtistProfile;
import com.muzin.mu.zin.entity.QUser;
import com.muzin.mu.zin.entity.TimePart;
import com.muzin.mu.zin.entity.artist.QArtistStyleMap;
import com.muzin.mu.zin.entity.instrument.InstrumentCategory;
import com.muzin.mu.zin.entity.instrument.QInstrument;
import com.muzin.mu.zin.entity.lesson.*;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.NumberTemplate;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class LessonRepositoryImpl implements LessonRepositoryCustom {

    private final JPAQueryFactory queryFactory;


    // 공개 레슨 검색용 동적 쿼리
    // 검색 조건에 맞는 ACTIVE 레슨만 조회하고, 시간 조건은 OPEN 타임슬롯 존재 여부로 필터링
    @Override
    public List<Lesson> searchPublicLessons(LessonSearchCond cond, Pageable pageable) {
        QLesson lesson = QLesson.lesson;
        QInstrument instrument = QInstrument.instrument;
        QLessonTimeSlot timeSlot = QLessonTimeSlot.lessonTimeSlot;
        QArtistStyleMap artistStyleMap = QArtistStyleMap.artistStyleMap;

        return queryFactory
                .selectFrom(lesson)
                .join(lesson.instrument, instrument).fetchJoin()
                .where(
                        lesson.deletedDt.isNull(),
                        lesson.status.eq(LessonStatus.ACTIVE),
                        modeCondition(cond.mode(), lesson),
                        keywordCondition(cond.keyword(), lesson),
                        styleTagCondition(cond.styleTagIds(), artistStyleMap, lesson),
                        instCategoryCondition(cond.instCategory(), lesson),
                        instIdsCondition(cond.instIds(), lesson),
                        timeSlotExistsCondition(cond, timeSlot, lesson)
                )
                .orderBy(lesson.lessonId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    // 공개 레슨 상세 조회
    // 레슨 상세 화면에 필요한 연관 데이터를 fetch join으로 함께 조회
    @Override
    public Optional<Lesson> findPublicDetailByIdDsl(Long lessonId) {
        QLesson lesson = QLesson.lesson;
        QArtistProfile artistProfile = QArtistProfile.artistProfile;
        QUser user = QUser.user;

        Lesson result = queryFactory
                .selectFrom(lesson)
                .join(lesson.artistProfile, artistProfile).fetchJoin()
                .join(artistProfile.user, user).fetchJoin()
                .where(lesson.lessonId.eq(lessonId))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    // 아티스트 상세 화면의 레슨 카드 목록 조회
    // 카드에 필요한 최소 정보만 LessonCardRow로 프로젝션해서 가져옴
    @Override
    public List<LessonCardRow> findArtistLessonCardRowDsl(Long artistProfileId) {
        QLesson lesson = QLesson.lesson;
        QInstrument instrument = QInstrument.instrument;

        return queryFactory
                .select(Projections.constructor(
                        LessonCardRow.class,
                        lesson.lessonId,
                        lesson.title,
                        lesson.price,
                        lesson.durationMin,
                        lesson.mode,
                        instrument.instId,
                        instrument.instName
                ))
                .from(lesson)
                .join(lesson.instrument, instrument)
                .where(
                        lesson.artistProfile.artistProfileId.eq(artistProfileId),
                        lesson.deletedDt.isNull(),
                        lesson.status.eq(LessonStatus.ACTIVE)
                )
                .orderBy(lesson.lessonId.desc())
                .fetch();
    }

    // 아티스트 상세 화면에서 선택한 레슨 1건의 상세 조회
    // 해당 아티스트 소속의 ACTIVE만 조회, 표시용 연관 데이터는 fetch join으로 함께 가져옴.
    @Override
    public Optional<Lesson> findArtistLessonDetailDsl(Long artistProfileId, Long lessonId) {
        QLesson lesson = QLesson.lesson;
        QArtistProfile artistProfile = QArtistProfile.artistProfile;
        QUser user = QUser.user;
        QInstrument instrument = QInstrument.instrument;

        Lesson result = queryFactory
                .selectFrom(lesson)
                .join(lesson.artistProfile, artistProfile).fetchJoin()
                .join(artistProfile.user, user).fetchJoin()
                .join(lesson.instrument, instrument).fetchJoin()
                .where(
                        lesson.lessonId.eq(lessonId),
                        artistProfile.artistProfileId.eq(artistProfileId),
                        lesson.deletedDt.isNull(),
                        lesson.status.eq(LessonStatus.ACTIVE)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

    // 레슨 모드
    private BooleanExpression modeCondition(LessonMode mode, QLesson lesson) {
        return mode == null ? null : lesson.mode.eq(mode);
    }

    // 검색창 키워드 입력
    private BooleanExpression keywordCondition(String keyword, QLesson lesson) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }

        String trimmed = keyword.trim();
        return lesson.title.containsIgnoreCase(trimmed)
                .or(lesson.description.coalesce("").containsIgnoreCase(trimmed));
    }

    // 아티스트 스타일 태그
    private BooleanExpression styleTagCondition(
            List<Long> styleTagIds,
            QArtistStyleMap artistStyleMap,
            QLesson lesson
    ) {
        if (styleTagIds == null || styleTagIds.isEmpty()) {
            return null;
        }

        return JPAExpressions
                .selectOne()
                .from(artistStyleMap)
                .where(
                        artistStyleMap.artistProfile.eq(lesson.artistProfile),
                        artistStyleMap.lessonStyleTag.lessonStyleTagId.in(styleTagIds)
                )
                .exists();
    }

    // 아티스트 악기 카테고리 검색
    private BooleanExpression instCategoryCondition(
            InstrumentCategory instCategory,
            QLesson lesson
    ) {
        return instCategory == null ? null : lesson.instrument.category.eq(instCategory);
    }

    // 악기 개별 검색(다수 가능)
    private BooleanExpression instIdsCondition(List<Long> instIds, QLesson lesson) {
        if (instIds == null || instIds.isEmpty()) {
            return null;
        }
        return lesson.instrument.instId.in(instIds);
    }

    // 시간(타임슬롯) 검색
    private BooleanExpression timeSlotExistsCondition(
            LessonSearchCond cond,
            QLessonTimeSlot timeSlot,
            QLesson lesson
    ) {
        boolean hasRange = cond.fromDt() != null && cond.toDt() != null;
        boolean hasWeekdays = cond.daysOfWeek() != null && !cond.daysOfWeek().isEmpty();
        boolean hasTimeParts = cond.timeParts() != null && !cond.timeParts().isEmpty();

        // 시간 관련 필터 없을때 시간 필터 제외
        if (!hasRange && !hasWeekdays && !hasTimeParts) {
            return null;
        }

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(timeSlot.lesson.eq(lesson));
        builder.and(timeSlot.status.eq(TimeSlotStatus.OPEN));

        if (hasRange) {
            builder.and(timeSlot.startDt.between(cond.fromDt(), cond.toDt()));
        }

        NumberTemplate<Integer> isDow = Expressions.numberTemplate(
                Integer.class,
                "cast(function('date_part', 'isdow', {0}) as integer)",
                timeSlot.startDt
        );

        NumberTemplate<Integer> hour = Expressions.numberTemplate(
                Integer.class,
                "cast(function('date_part', 'hour', {0}) as integer)",
                timeSlot.startDt
        );

        if (hasWeekdays) {
            builder.and(isDow.in(cond.daysOfWeek()));
        }

        if (hasTimeParts) {
            builder.and(timePartCondition(cond.timeParts(), hour));
        }

        return JPAExpressions
                .selectOne()
                .from(timeSlot)
                .where(builder)
                .exists();

    }

    private BooleanExpression timePartCondition(List<TimePart> timeParts, NumberExpression<Integer> hour) {
        if (timeParts == null || timeParts.isEmpty()) {
            return null;
        }

        BooleanExpression expression = null;

        if (timeParts.contains(TimePart.DAWN)) {
            expression = or(expression, hour.between(0, 5));
        }
        if (timeParts.contains(TimePart.MORNING)) {
            expression = or(expression, hour.between(6, 11));
        }
        if (timeParts.contains(TimePart.AFTERNOON)) {
            expression = or(expression, hour.between(12, 17));
        }
        if (timeParts.contains(TimePart.EVENING)) {
            expression = or(expression, hour.between(18, 23));
        }

        return expression;
    }

    private BooleanExpression or(BooleanExpression base, BooleanExpression addition) {
        return base == null ? addition : base.or(addition);
    }
}
