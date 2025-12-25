package com.muzin.mu.zin.entity;

import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import com.muzin.mu.zin.entity.instrument.Instrument;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private Long artistProfileId;

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

    @Column(name = "submitted_dt")
    private LocalDateTime submittedDt;

    @Column(name = "reviewed_dt")
    private LocalDateTime reviewedDt;

    @Column(name = "rejected_reason_title", length = 200)
    private String rejectReasonTitle;

    @Lob
    @Column(name = "rejected_reason")
    private String rejectedReason;

    // 아티스트가 추가한 악기 리스트(악기 테이블 매핑)
    @Builder.Default
    @OneToMany(mappedBy = "artistProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ArtistInstrument> artistInstruments = new ArrayList<>();

    // 수정을 위한 메서드
    public void updateProfile(String bio, String career, String majorName) {
        this.bio = bio;
        this.career = career;
        this.majorName = majorName;
    }

    // 악기 매핑 편의 매서드
    public void addInstrument (Instrument instrument) {
        this.artistInstruments.add(new ArtistInstrument(this, instrument));
    }

    public void clearInstruments() {
        this.artistInstruments.clear(); // orphanRemoval=true 면 DB join row도 삭제됨
    }


}
