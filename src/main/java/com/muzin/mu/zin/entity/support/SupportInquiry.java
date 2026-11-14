package com.muzin.mu.zin.entity.support;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 운영자 문의 (V15): 로그인한 사용자의 질문 1개 + 관리자 답변 1개
@Entity
@Table(name = "support_inquiry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SupportInquiry extends BaseTimeEntity {

    public enum Category { ACCOUNT, RESERVATION, REPORT, BUG, ETC }

    public enum Status { OPEN, ANSWERED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "support_id")
    private Long supportId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(nullable = false, length = 2000)
    private String content;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(name = "reply_content", length = 2000)
    private String replyContent;

    @Column(name = "replied_dt")
    private LocalDateTime repliedDt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replied_by")
    private User repliedBy;

    public void answer(User admin, String content) {
        this.replyContent = content;
        this.repliedBy = admin;
        this.repliedDt = TimeDefaults.nowKst();
        this.status = Status.ANSWERED;
    }
}
