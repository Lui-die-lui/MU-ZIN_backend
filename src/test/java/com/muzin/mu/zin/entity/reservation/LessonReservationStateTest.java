package com.muzin.mu.zin.entity.reservation;

import com.muzin.mu.zin.entity.lesson.LessonTimeSlot;
import com.muzin.mu.zin.entity.lesson.TimeSlotStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 예약/슬롯 상태 전이 규칙 (DB 없이 도메인 메서드만 검증)
class LessonReservationStateTest {

    private LessonReservation reservation(ReservationStatus status) {
        return LessonReservation.builder().status(status).priceAtBooking(0).build();
    }

    private LessonTimeSlot slot(TimeSlotStatus status) {
        return LessonTimeSlot.builder().status(status).build();
    }

    @Test
    @DisplayName("요청 -> 확정 -> 완료대기 -> 아티스트 완료")
    void happyPath() {
        LessonReservation r = reservation(ReservationStatus.REQUESTED);
        r.confirm();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(r.getConfirmedDt()).isNotNull();

        r.markCompletionPending();
        r.completeByArtist();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(r.getCompletionSource()).isEqualTo(CompletionSource.ARTIST);
    }

    @Test
    @DisplayName("응답 없는 요청은 만료될 수 있고, 만료 시각이 기록됨")
    void expireRequested() {
        LessonReservation r = reservation(ReservationStatus.REQUESTED);
        r.expire();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(r.getExpiredDt()).isNotNull();
    }

    @Test
    @DisplayName("요청 상태가 아니면 만료 불가 (확정된 예약이 만료되면 안 됨)")
    void expireOnlyRequested() {
        for (ReservationStatus s : ReservationStatus.values()) {
            if (s == ReservationStatus.REQUESTED) continue;
            assertThatThrownBy(() -> reservation(s).expire())
                    .as("status=%s", s)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("만료/거절/완료대기/완료된 예약은 취소 불가")
    void cancelOnlyActive() {
        for (ReservationStatus s : new ReservationStatus[]{
                ReservationStatus.EXPIRED, ReservationStatus.REJECTED, ReservationStatus.CANCELED,
                ReservationStatus.COMPLETION_PENDING, ReservationStatus.COMPLETED}) {
            assertThatThrownBy(() -> reservation(s).cancel())
                    .as("status=%s", s)
                    .isInstanceOf(IllegalStateException.class);
        }
        LessonReservation requested = reservation(ReservationStatus.REQUESTED);
        requested.cancel();
        assertThat(requested.getStatus()).isEqualTo(ReservationStatus.CANCELED);
    }

    @Test
    @DisplayName("확정은 요청 상태에서만 가능")
    void confirmOnlyRequested() {
        assertThatThrownBy(() -> reservation(ReservationStatus.EXPIRED).confirm())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reservation(ReservationStatus.CANCELED).confirm())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("슬롯: OPEN -> PENDING -> BOOKED, 예약 요청 중인 슬롯은 만료 시 CLOSED 가능")
    void slotTransitions() {
        LessonTimeSlot s = slot(TimeSlotStatus.OPEN);
        s.pending();
        assertThat(s.getStatus()).isEqualTo(TimeSlotStatus.PENDING);
        s.book();
        assertThat(s.getStatus()).isEqualTo(TimeSlotStatus.BOOKED);

        LessonTimeSlot pending = slot(TimeSlotStatus.PENDING);
        pending.close();
        assertThat(pending.getStatus()).isEqualTo(TimeSlotStatus.CLOSED);

        // 이미 요청이 들어간 슬롯에 또 요청 불가
        assertThatThrownBy(() -> slot(TimeSlotStatus.PENDING).pending())
                .isInstanceOf(IllegalStateException.class);
        // 예약 확정된 슬롯은 닫을 수 없음 (closeFromBooked 로만)
        assertThatThrownBy(() -> slot(TimeSlotStatus.BOOKED).close())
                .isInstanceOf(IllegalStateException.class);
    }
}
