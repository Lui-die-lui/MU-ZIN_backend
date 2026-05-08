package com.muzin.mu.zin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 관심 아티스트
@Entity
@Table(
        name = "artist_favorite",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_artist_favorite_user_artist",
                        columnNames = {"user_id", "artist_profile_id"}
                )
        },
        indexes = {
                @Index(name = "idx_artist_favorite_user_id", columnList = "user_id"),
                @Index(name = "idx_artist_favorite_artist_profile_id", columnList = "artist_profile_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ArtistFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "artist_favorite_id")
    private Long artistFavoriteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_profile_id", nullable = false)
    private ArtistProfile artistProfile;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}