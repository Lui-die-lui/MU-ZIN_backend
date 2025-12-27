package com.muzin.mu.zin.entity.lesson;

import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "title", length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode")
    @Builder.Default
    private LessonMode mode = LessonMode.REQUEST_ONLY;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private LessonStatus status = LessonStatus.ACTIVE;

    // 서비스 로직 개선 - 빈 값으로 덮어쓰기 막음
    public void applyUpdate(String title, LessonMode mode) {
        if (title != null && !title.isBlank()) this.title = title;
        if (mode != null) this.mode = mode;
    }

    public void changeStatus(LessonStatus status) {
        if (status != null) this.status = status;
    }
}
