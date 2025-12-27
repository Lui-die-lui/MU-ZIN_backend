package com.muzin.mu.zin.entity.lesson;

import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "lesson_styles_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LessonStyleTag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lesson_style_tag_id", nullable = false)
    private Long lessonStyleTagId;

    @Column(name = "style_name", nullable = false, length = 25)
    private String styleName;

}
