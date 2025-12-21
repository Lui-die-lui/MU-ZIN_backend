package com.muzin.mu.zin.entity;

// 아티스트 - 악기 매핑 테이블

import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "artist_instruments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_artist_instrument_pair",
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
}
