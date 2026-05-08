package com.muzin.mu.zin.entity.Review;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "review_keyword_map",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_review_keyword_map",
                        columnNames = {"review_id", "review_keyword_id"}
                )
        },
        indexes = {
                @Index(name = "idx_review_keyword_map_review_id", columnList = "review_id"),
                @Index(name = "idx_review_keyword_map_keyword_id", columnList = "review_keyword_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ReviewKeywordMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_keyword_map_id")
    private Long reviewKeywordMapId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_keyword_id", nullable = false)
    private ReviewKeyword reviewKeyword;
}