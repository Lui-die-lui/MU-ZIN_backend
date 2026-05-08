package com.muzin.mu.zin.entity.Review;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "review_keyword")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ReviewKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_keyword_id")
    private Long reviewKeywordId;

    @Column(name = "keyword_name", nullable = false, length = 50)
    private String keywordName;

    // 리뷰 키워드 노출 순서
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }
}
