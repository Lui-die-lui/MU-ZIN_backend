package com.muzin.mu.zin.entity.artist;

import com.muzin.mu.zin.entity.ArtistProfile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "artist_service_region",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_artist_service_region_unique",
                        columnNames = {"artist_profile_id", "region1_depth_name", "region2_depth_name"}
                )
        }
)
// 시/도 + 시/군/구 단위 서비스 지역 1개
public class ArtistServiceRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "artist_service_region_id")
    private Long artistServiceRegionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_profile_id", nullable = false)
    private ArtistProfile artistProfile;

    @Column(name = "region1_depth_name", length = 40, nullable = false)
    private String region1DepthName;

    @Column(name = "region2_depth_name", length = 40, nullable = false)
    private String region2DepthName;

    // static 메서드(of) 내부에서 사용할 생성자
    // 외부에서 직접 new 하지 않도록 protected로 제한
    protected ArtistServiceRegion(
            ArtistProfile artistProfile,
            String region1DepthName,
            String region2DepthName
    ) {
        this.artistProfile = artistProfile;
        this.region1DepthName = region1DepthName;
        this.region2DepthName = region2DepthName;
    }

    // 아티스트에 속한 서비스 가능 지역 엔티티 생성
    // ex: 부산광역시 / 연제구
    public static ArtistServiceRegion of(
            ArtistProfile artistProfile,
            String region1DepthName,
            String region2DepthName
    ) {
        return new ArtistServiceRegion(artistProfile, region1DepthName, region2DepthName);
    }
}