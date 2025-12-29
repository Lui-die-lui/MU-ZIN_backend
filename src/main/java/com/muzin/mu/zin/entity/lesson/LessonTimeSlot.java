package com.muzin.mu.zin.entity.lesson;

import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "lesson_time_slot",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_lesson_slot_start", columnNames = {"lesson_id", "start_dt"})
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LessonTimeSlot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "time_slot_id", nullable = false)
    private Long timeSlotId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "start_dt", nullable = false)
    private LocalDateTime startDt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TimeSlotStatus status = TimeSlotStatus.OPEN;

    public void open() {this.status = TimeSlotStatus.OPEN;}
    public void close() {this.status = TimeSlotStatus.CLOSED;}
    public void book() {this.status = TimeSlotStatus.BOOKED;}
}
