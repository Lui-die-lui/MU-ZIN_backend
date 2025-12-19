package com.muzin.mu.zin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// LAZY 연관에서 toString()터지는거 방지
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "oauth2_user",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_oauth2_user", columnNames = "provider_user_id"),
                @UniqueConstraint(name = "uk_oauth2_user_provider", columnNames = {"user_id","provider"})
        }
)
public class OAuth2UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long oauth2UserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @CreationTimestamp
    @Column(name = "create_dt", updatable = false)
    private LocalDateTime createDt;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private LocalDateTime updateDt;
}
