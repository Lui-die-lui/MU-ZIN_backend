package com.muzin.mu.zin.entity;

import com.muzin.mu.zin.entity.artist.ArtistServiceRegion;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import com.muzin.mu.zin.entity.instrument.Instrument;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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

//    @Lob // Large Object - TEXT 같은...
    @Column(name = "career", nullable = false, columnDefinition = "TEXT")
    private String career;

    @Column(name = "major_name", nullable = false, length = 50)
    private String majorName;

    @Column(name = "submitted_dt")
    private LocalDateTime submittedDt;

    @Column(name = "reviewed_dt")
    private LocalDateTime reviewedDt;

    @Column(name = "rejected_reason_title", length = 200)
    private String rejectReasonTitle;

//    @Lob
    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    private String rejectedReason;

    // 아티스트가 추가한 악기 리스트(악기 테이블 매핑)
    @Builder.Default
    @OneToMany(mappedBy = "artistProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ArtistInstrument> artistInstruments = new ArrayList<>();

    // 스튜디오/거점 주소 (null 가능)
    // 시/도
    @Column(name = "region1_depth_name", length = 40)
    private String region1DepthName;

    // 시/군/구
    @Column(name = "region2_depth_name", length = 40)
    private String region2DepthName;

    // 읍/면/동
    @Column(name = "region3_depth_name", length = 40)
    private String region3DepthName;

    // 화면 표시용 주소
    @Column(name = "address_label", length = 120)
    private String addressLabel;

    // precision = 소숫점 앞 뒤 합쳐서 총 10자리 저장 가능(전체 자리 최대 개수)
    // scale = 소수점 이하 저장되는 자리 개수
    // BigDecimal = 정밀 수치 숫자 타입(소수의 오차를 적게, 정확하게 다룸)
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    // 서비스 지역 리스트 매핑(검색용 서비스 가능 지역 목록)
    @Builder.Default
    @OneToMany(mappedBy = "artistProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ArtistServiceRegion> serviceRegions = new ArrayList<>();


    // 수정을 위한 메서드
    public void updateProfile(String bio, String career, String majorName) {
        this.bio = bio;
        this.career = career;
        this.majorName = majorName;
    }

    // 아티스트 전환 제출 날짜
    public void markSubmitted() {
        this.submittedDt = LocalDateTime.now();
    }

    // 악기 매핑 편의 매서드
    public void addInstrument (Instrument instrument) {
        this.artistInstruments.add(new ArtistInstrument(this, instrument));
    }

    public void clearInstruments() {
        this.artistInstruments.clear(); // orphanRemoval=true 면 DB join row도 삭제됨
    }

    public void markReviewed() {
        this.reviewedDt = LocalDateTime.now();
    }

    // 반려 사유 클리어 함수
    public void clearRejectReason() {
        this.rejectReasonTitle = null;
        this.rejectedReason = null;
    }

    public void setRejectReason(String title, String reason) {
        this.rejectReasonTitle = title;
        this.rejectedReason = reason;
    }

    // 내 스튜디오 같은 주요 활동 지역
    public void updateRegion(
            String region1DepthName,
            String region2DepthName,
            String region3DepthName,
            String addressLabel,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.region1DepthName = region1DepthName;
        this.region2DepthName = region2DepthName;
        this.region3DepthName = region3DepthName;
        this.addressLabel = addressLabel;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void clearRegion() {
        this.region1DepthName = null;
        this.region2DepthName = null;
        this.region3DepthName = null;
        this.addressLabel = null;
        this.latitude = null;
        this.longitude = null;
    }

    // 서비스 지역 업데이트 및 지우기
    public void addServiceRegion(String region1DepthName, String region2DepthName) {
        this.serviceRegions.add(ArtistServiceRegion.of(this, region1DepthName, region2DepthName));
    }

    public void clearServiceRegions() {
        this.serviceRegions.clear();
    }
}



