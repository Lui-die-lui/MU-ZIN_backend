package com.muzin.mu.zin.entity.Review;

import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;


// 아티스트가 작성하는 대댓 리뷰
@Entity
@Table(
        name = "review_reply",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_review_reply_review",
                        columnNames = "review_id"
                )
        },
        indexes = {
                @Index(name = "idx_review_reply_artist_profile_id", columnList = "artist_profile_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ReviewReply extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_reply_id")
    private Long reviewReplyId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_profile_id", nullable = false)
    private ArtistProfile artistProfile;

    @Column(nullable = false, length = 255)
    private String content;

    @Column(name = "delete_dt")
    private LocalDateTime deleteDt;

    public void update(String content) {
        this.content = content;
    }

    public void softDelete() {
        this.deleteDt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deleteDt != null;
    }
}
