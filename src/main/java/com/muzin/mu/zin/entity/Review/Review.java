package com.muzin.mu.zin.entity.Review;

import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import com.muzin.mu.zin.entity.lesson.Lesson;
import com.muzin.mu.zin.entity.reservation.LessonReservation;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "review",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_review_reservation",
                        columnNames = "reservation_id"
                )
        },
        // db 인덱스 설정
        // 조회 시 색인표 역할
        indexes = {
                @Index(name = "idx_review_lesson_id", columnList = "lesson_id"),
                @Index(name = "idx_review_artist_profile_id", columnList = "artist_profile_id"),
                @Index(name = "idx_review_reviewer_user_id", columnList = "reviewer_user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Review extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    // 같은 레슨을 다시 들어도 reservation id 가 다르면 또 달 수 있음
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private LessonReservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_profile_id", nullable = false)
    private ArtistProfile artistProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_user_id", nullable = false)
    private User reviewUser;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 255)
    private String content;

    @Column(name = "delete_dt")
    private LocalDateTime deleteDt;

    public void update(Integer rating, String content) {
        this.rating = rating;
        this.content = content;
    }

    public void softDelete() {
        this.deleteDt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deleteDt != null;
    }
}
