package com.muzin.mu.zin.entity.lesson;

import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lessons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Lesson extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_profile_id", nullable = false)
    private ArtistProfile artistProfile;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    // 레슨 소개
    @Column(name = "description", columnDefinition = "text")
    private String description;

    // 레슨 요청시 필수 사항 안내(준비물 등)
    @Column(name = "requirement_text", columnDefinition = "text")
    private String requirementText;

    @Column(name = "price")
    private Integer price;

    // 소요 시간
    @Column(name = "duration_min", nullable = false)
    private Integer durationMin;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode")
    @Builder.Default
    private LessonMode mode = LessonMode.REQUEST_ONLY;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private LessonStatus status = LessonStatus.ACTIVE;

    // 삭제 날짜를 따로 지정해서 지연 삭제
    @Column(name = "deleted_dt")
    private LocalDateTime deletedDt;

    public boolean isDeleted() {
        return deletedDt != null; // 레슨이 삭제된 상태인지 확인(삭제 날짜가 안찍혀있으면 삭제 안된거)
    }

    public void markDeleted() {
        this.status = LessonStatus.INACTIVE;
        this.deletedDt = LocalDateTime.now();
    }

    public void restore() {
        this.status = LessonStatus.ACTIVE;
        this.deletedDt = null;
    }

    // 서비스 로직 개선 - 빈 값(null)으로 덮어쓰기 막음
    public void applyUpdate(String title, LessonMode mode,
                            String description, String requirementText,
                            Integer price, Integer durationMin) {
        if (title != null && !title.isBlank()) this.title = title;
        if (mode != null) this.mode = mode;
        if (description != null) this.description = description;
        if (requirementText != null) this.requirementText = requirementText;
        if (price != null) this.price = price;
        if (durationMin != null) this.durationMin = durationMin;
    }

    public void changeStatus(LessonStatus status) {
        if (status != null) this.status = status;
    }
}
