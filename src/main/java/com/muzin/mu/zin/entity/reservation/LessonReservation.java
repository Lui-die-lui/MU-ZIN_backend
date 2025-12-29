package com.muzin.mu.zin.entity.reservation;

import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.lesson.LessonTimeSlot;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table (
        name = "lesson_reservation",
        uniqueConstraints = {
                // 한 슬롯은 한 예약만 갖게 됨 - 동일 시간 중복 예약 db에서 막음
                @UniqueConstraint(name = "uk_lesson_reservation_slot", columnNames = "slot_id")
        }
)
@EntityListeners(AutoCloseable.class) // 이게 뭔데
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LessonReservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Lesson_id 같이 들고감
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    // time_slot_id UK
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false, unique = true)
    private LessonTimeSlot timeSlot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;

    // 예약 당시 가격
    @Column(name = "price_at_booking", nullable = false)
    private Integer priceAtBooking;

    // 예약 요청시 보내는 메시지
    @Column(name = "requested_msg", columnDefinition = "text")
    private String requestedMsg;

    // 예약 요청 당시 날짜와 시간
    @Column(name = "requested_dt", nullable = false)
    private LocalDateTime requestedDt;

    // 예약 성공 날짜와 시간
    @Column(name = "confirmed_dt")
    private LocalDateTime confirmedDt;

    // 취소 날짜와 시간
    @Column(name = "canceled_dt")
    private LocalDateTime canceledDt;


    // 도메인 메서드

    public void confirm() {
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedDt = LocalDateTime.now();
    }

    public void reject() {
        this.status = ReservationStatus.REJECTED;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELED;
        this.canceledDt = LocalDateTime.now();
    }



}
