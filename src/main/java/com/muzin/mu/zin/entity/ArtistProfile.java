package com.muzin.mu.zin.entity;

import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 의미 없는 무분별한 기본 생성자 생성을 막음
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "artist_profile",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_artist_profile_user", columnNames = {"user_id"})
        }
)
public class ArtistProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "artist_profile_id", nullable = false)
    private Long artist_profile_id;

    // 유저당 아티스트 프로필 1개
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // 아티스트 소개
    @Column(name = "bio", nullable = false, length = 255)
    private String bio;

    @Lob // Large Object - TEXT 같은...
    @Column(name = "career", nullable = false)
    private String career;

    @Column(name = "major_name", nullable = false, length = 50)
    private String majorName;

    @Column(name = "submitted_dt", nullable = false)
    private LocalDateTime submittedDt;

    @Column(name = "reviewed_dt")
    private LocalDateTime reviewedDt;

    @Column(name = "rejected_reason_title", length = 200)
    private String rejectReasonTitle;

    @Lob
    @Column(name = "rejected_reason")
    private String rejectedReason;

    // 수정을 위한 메서드
    public void updateProfile(String bio, String career, String majorName) {
        this.bio = bio;
        this.career = career;
        this.majorName = majorName;
    }


}
