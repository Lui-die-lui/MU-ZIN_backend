package com.muzin.mu.zin.entity.instrument;

// 악기명 = UNIQUE

import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "instruments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_instruments_inst_name", columnNames = "inst_name")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Instrument extends BaseTimeEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "inst_id")
        private Long instId;

        // 요청하는 마스터 seed는 null 가능
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "created_by_user_id")
        private User createdBy;

        @Column(name = "inst_name", nullable = false, length = 20)
        private String instName;

        @Enumerated(EnumType.STRING)
        @Column(name = "category", nullable = false, length = 30)
        @Builder.Default
        private InstrumentCategory category = InstrumentCategory.ETC;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 20)
        @Builder.Default
        private InstrumentStatus status = InstrumentStatus.APPROVED;

        @Lob
        @Column(name = "reject_reason")
        private String rejectReason;

        @Lob
        @Column(name = "reviewed_dt")
        private LocalDateTime reviewedDt;

        // 도메인 메서드

        public void rename(String newName) {
                this.instName = newName;
        }

        public void changeCategory(InstrumentCategory newCartegory) {
                this.category = newCartegory;
        }

        // 요청 등록(유저가 추가 요청한 악기)
        public static Instrument request(User requester, String name, InstrumentCategory category) {
                return Instrument.builder()
                        .createdBy(requester)
                        .instName(name)
                        .category(category)
                        .status(InstrumentStatus.PENDING)
                        .build();
        }

        public void approve() {
                this.status = InstrumentStatus.APPROVED;
                this.rejectReason = null;
                this.reviewedDt = LocalDateTime.now();
        }

        public void reject(String reason) {
                this.status = InstrumentStatus.REJECTED;
                this.rejectReason = reason;
                this.reviewedDt = LocalDateTime.now();
        }

        public void deactivate() {
                this.status = InstrumentStatus.INACTIVE;
        }

        public void activate() {
                this.status = InstrumentStatus.APPROVED;
        }
}
