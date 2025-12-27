package com.muzin.mu.zin.entity.lesson;


import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "lesson_style_map",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_lesson_style_map_lesson_tag",
                        columnNames = {"lesson_id", "lesson_style_tag_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LessonStyleMap extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lesson_style_map_id", nullable = false)
    private Long lessonStyleMapId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_style_tag_id", nullable = false)
    private LessonStyleTag lessonStyleTag;
}
