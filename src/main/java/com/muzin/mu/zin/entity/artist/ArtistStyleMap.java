package com.muzin.mu.zin.entity.artist;

import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import com.muzin.mu.zin.entity.lesson.LessonStyleTag;
import jakarta.persistence.*;
import lombok.*;

// 아티스트가 레슨 스타일을 가지고있는게 사람 - 강사 특성이기 때문에
@Entity
@Table(
        name = "artist_style_map",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_artist_style_map_artist_tag",
                        columnNames = {"artist_profile_id", "lesson_style_tag_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ArtistStyleMap extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long artistStyleMapId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_profile_id", nullable = false)
    private ArtistProfile artistProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_style_tag_id", nullable = false)
    private LessonStyleTag lessonStyleTag;
}
