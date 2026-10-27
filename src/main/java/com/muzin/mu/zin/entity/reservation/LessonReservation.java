package com.muzin.mu.zin.entity.reservation;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.lesson.LessonTimeSlot;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table (
        name = "lesson_reservation"
//        uniqueConstraints = {
//                // 한 슬롯은 한 예약만 갖게 됨 - 동일 시간 중복 예약 db에서 막음
//                @UniqueConstraint(name = "uk_lesson_reservation_slot", columnNames = "slot_id")
//        }
)
//@EntityListeners(AutoCloseable.class) // 이게 뭔데
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

    // time_slot_id UK(원래 OneToOne - 타임슬롯 하나당 하나의 예약이 들어감. 새로운 행 추가 불가
    // But 취소된 상황에서 같은 슬롯에 다른 예약자가 예약할때도 막힘 )
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "canceled_by", length = 10)
    private CanceledBy canceledBy;

    @Column(name = "completed_dt")
    private LocalDateTime completedDt;

    @Column(name = "completion_pending_dt")
    private LocalDateTime completionPendingDt;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_source")
    private CompletionSource completionSource;

    @Column(name = "expired_dt")
    private LocalDateTime expiredDt;

    // 낙관적 락: 아티스트 확정과 유저 취소가 동시에 들어오는 등 같은 예약을 동시에 바꾸면 한쪽은 409
    @Version
    @Column(name = "version", nullable = false)
    private Long version;


    // 도메인 메서드

    public void confirm() {
        if (this.status != ReservationStatus.REQUESTED) {
            throw new IllegalStateException("확정할 수 없는 예약 상태입니다.");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedDt = TimeDefaults.nowKst();
    }

    public void reject() {
        if (this.status != ReservationStatus.REQUESTED) {
            throw new IllegalStateException("거절할 수 없는 예약 상태입니다.");
        }
        this.status = ReservationStatus.REJECTED;
    }

    public void cancel(CanceledBy by) {
        if (this.status != ReservationStatus.REQUESTED && this.status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("취소할 수 없는 예약 상태입니다.");
        }

        this.status = ReservationStatus.CANCELED;
        this.canceledDt = TimeDefaults.nowKst();
        this.canceledBy = by;
    }

    // 레슨 시작 시점에 status를 바꿔서 완료 흐름을 조금 더 명확하게 해줌
    public void markCompletionPending() {
        if (this.status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("완료 대기 상태로 변경할 수 없는 예약 상태입니다.");
        }

        this.status = ReservationStatus.COMPLETION_PENDING;
        this.completionPendingDt = TimeDefaults.nowKst();
    }

    public void completeByArtist() {
        if (this.status != ReservationStatus.COMPLETION_PENDING) {
            throw new IllegalStateException("완료 처리할 수 없는 예약 상태입니다.");
        }

        this.status = ReservationStatus.COMPLETED;
        this.completedDt = TimeDefaults.nowKst();
        this.completionSource = CompletionSource.ARTIST;
    }

    // 아티스트가 응답하지 않은 채 레슨 시작 시간이 지난 요청
    public void expire() {
        if (this.status != ReservationStatus.REQUESTED) {
            throw new IllegalStateException("만료 처리할 수 없는 예약 상태입니다.");
        }
        this.status = ReservationStatus.EXPIRED;
        this.expiredDt = TimeDefaults.nowKst();
    }

    public void autoComplete() {
        if (this.status != ReservationStatus.COMPLETION_PENDING) {
            throw new IllegalStateException("자동 완료 처리할 수 없는 예약 상태입니다.");
        }

        this.status = ReservationStatus.COMPLETED;
        this.completedDt = TimeDefaults.nowKst();
        this.completionSource = CompletionSource.SYSTEM;
    }



}
