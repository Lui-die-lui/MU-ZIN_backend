package com.muzin.mu.zin.entity.lesson;

import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
        name = "lesson_recurrence_rule",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_lesson_recurrence_rule_lesson", columnNames = "lesson_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LessonRecurrenceRule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ruleId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, unique = true)
    private Lesson lesson; // lesson 1개당 1개의 룰 적용

    @Column(nullable = false)
    private Boolean enabled;

    @Column(nullable = false, length = 64)
    private String timezone; // "Asia/Seoul"

    @Column(name = "days_of_week_mask", nullable = false)
    private Integer daysOfWeekMask;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    // 쉬는 시간 (수업 간 간격)
    @Column(name = "interval_min", nullable = false)
    private Integer intervalMin;

    // 반복 주간
    @Column(name = "weeks_ahead", nullable = false)
    private Integer weeksAhead;

    // 이전 작업 반복 기억
    @Setter
    @Column(name = "materialized_until")
    private LocalDate materializedUntil;

    public void applyUpdate(Boolean enabled, String timezone, Integer mask,
                            LocalTime startTime, LocalTime endTime, Integer intervalMin, Integer weeksAhead) {
        this.enabled = enabled;
        this.timezone = timezone;
        this.daysOfWeekMask = mask;
        this.startTime = startTime;
        this.endTime = endTime;
        this.intervalMin = intervalMin;
        this.weeksAhead = weeksAhead;
    }

}

