package com.muzin.mu.zin.entity;

// 아티스트 - 악기 매핑 테이블

import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import com.muzin.mu.zin.entity.instrument.Instrument;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
//@AllArgsConstructor - 생성은 생성자/팩토리로만 제한하는게 안전
//@Builder - 조인 엔티티에서는 빌더 제거
@Entity
@Table(
        name = "artist_instruments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_artist_profile_instrument",
                        columnNames = {"artist_profile_id", "inst_id"}
                )
        }
)
public class ArtistInstrument extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "artist_instrument_id", nullable = false)
    private Long artistInstrumentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_profile_id", nullable = false)
    private ArtistProfile artistProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inst_id", nullable = false)
    private Instrument instrument;

    public ArtistInstrument(ArtistProfile artistProfile, Instrument instrument) {
        this.artistProfile = artistProfile;
        this.instrument = instrument;
    }
}
