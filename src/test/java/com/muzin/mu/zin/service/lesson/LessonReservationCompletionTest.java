package com.muzin.mu.zin.service.lesson;

import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.lesson.LessonTimeSlot;
import com.muzin.mu.zin.entity.lesson.TimeSlotStatus;
import com.muzin.mu.zin.entity.reservation.LessonReservation;
import com.muzin.mu.zin.entity.reservation.ReservationStatus;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.repository.lesson.LessonReservationRepository;
import com.muzin.mu.zin.repository.lesson.LessonTimeSlotRepository;
import com.muzin.mu.zin.service.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LessonReservationCompletionTest {

    private LessonReservationRepository reservationRepository;
    private LessonReservationService service;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(LessonReservationRepository.class);
        service = new LessonReservationService(
                reservationRepository,
                mock(LessonTimeSlotRepository.class),
                mock(UserRepository.class),
                mock(ApplicationEventPublisher.class),
                mock(ChatService.class)
        );
    }

    @Test
    @DisplayName("자동 완료 시 예약은 COMPLETED, 슬롯은 CLOSED로 변경")
    void autoCompletionClosesBookedSlot() {
        LessonTimeSlot slot = LessonTimeSlot.builder().status(TimeSlotStatus.BOOKED).build();
        LessonReservation reservation = LessonReservation.builder()
                .status(ReservationStatus.COMPLETION_PENDING)
                .timeSlot(slot)
                .user(User.builder().userId(10L).build())
                .build();
        when(reservationRepository.findReservationsToAutoComplete(any())).thenReturn(List.of(reservation));

        service.autoCompleteReservations();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(slot.getStatus()).isEqualTo(TimeSlotStatus.CLOSED);
    }

    @Test
    @DisplayName("아티스트 수동 완료 시 예약은 COMPLETED, 슬롯은 CLOSED로 변경")
    void artistCompletionClosesBookedSlot() {
        LessonTimeSlot slot = LessonTimeSlot.builder().status(TimeSlotStatus.BOOKED).build();
        LessonReservation reservation = LessonReservation.builder()
                .reservationId(20L)
                .status(ReservationStatus.COMPLETION_PENDING)
                .timeSlot(slot)
                .user(User.builder().userId(10L).build())
                .build();
        when(reservationRepository.findByReservationIdAndLesson_ArtistProfile_User_UserId(20L, 4L))
                .thenReturn(Optional.of(reservation));

        service.completeReservationByArtist(20L, 4L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(slot.getStatus()).isEqualTo(TimeSlotStatus.CLOSED);
    }
}
