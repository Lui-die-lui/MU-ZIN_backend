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

    @Column(name = "end_dt", nullable = false)
    private LocalDateTime endDt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TimeSlotStatus status = TimeSlotStatus.OPEN;

    public void pending() {
        if (this.status != TimeSlotStatus.OPEN) {
            throw new IllegalStateException("OPEN 슬롯만 PENDING으로 전환 가능");
        }
        this.status = TimeSlotStatus.PENDING;
    }

    public void book() {
        if (this.status != TimeSlotStatus.PENDING) {
            throw new IllegalStateException("PENDING 만 BOOKED로 확정 가능");
        }
        this.status = TimeSlotStatus.BOOKED;
    }

    // 메서드 꼬일 확률이 있기 때문에 자동 예약 확정은 따로
    public void bookAuto() {
        if (this.status != TimeSlotStatus.OPEN && this.status != TimeSlotStatus.PENDING) {
            throw new IllegalStateException("OPEN 또는 PENDING 슬롯만 BOOKED로 확정 가능");
        }
        this.status = TimeSlotStatus.BOOKED;
    }

    public void reopenFromBooked() {
        if (this.status != TimeSlotStatus.BOOKED) {
            throw new IllegalStateException("BOOKED 슬롯만 재오픈할 수 있습니다.");
        }
        this.status = TimeSlotStatus.OPEN;
    }

    public void open() {
        if (this.status != TimeSlotStatus.PENDING) {
        throw new IllegalStateException("PENDING 슬롯만 OPEN으로 복귀 가능");
    }
        this.status = TimeSlotStatus.OPEN;
    }

    public void close() {
        if (this.status == TimeSlotStatus.BOOKED) {
            throw new IllegalStateException("BOOKED 슬롯은 CLOSED로 바꿀 수 없습니다.");
        }
        this.status = TimeSlotStatus.CLOSED;
    }

    // UX 적으로 봤을때 필요함
    // - 선택: 아티스트가 확정된 예약을 취소한다 - 개인 사정일 경우가 많고 그 시간은 열리지 않아야할 경우가 있음
    public void closeFromBooked() {
        if (this.status != TimeSlotStatus.BOOKED) {
            throw new IllegalStateException("BOOKED 슬롯만 CLOSE 할 수 있습니다.");
        }
        this.status = TimeSlotStatus.CLOSED;
    }

}
