package com.muzin.mu.zin.entity;

import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "instruments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_instrument_name", columnNames = {"inst_name"})
        }
)
public class Instrument extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inst_id", nullable = false)
    private Long instId;

    // 누가 추가 요청했는지(관리용) - 마스터 seed는 null이어도 ok
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "inst_name", nullable = false, length = 50)
    private String instName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InstrumentStatus status;

    @Lob
    @Column(name = "reject_reason")
    private String rejectedReason;

    @Column(name = "reviewed_dt")
    private LocalDateTime reviewedDt;
}
