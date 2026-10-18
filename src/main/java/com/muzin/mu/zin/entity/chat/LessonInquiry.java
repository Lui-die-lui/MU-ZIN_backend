package com.muzin.mu.zin.entity.chat;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 레슨 문의 (V6): 레슨이 없어도 아티스트에게 질문 1개, 아티스트는 답변 1개 또는 채팅으로 넘김
@Entity
@Table(name = "lesson_inquiry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LessonInquiry extends BaseTimeEntity {

    public enum Status { OPEN, REPLIED, CHAT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id")
    private Long inquiryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_profile_id", nullable = false)
    private ArtistProfile artistProfile;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "reply_content", length = 1000)
    private String replyContent;

    @Column(name = "replied_dt")
    private LocalDateTime repliedDt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    // 방이 지워지면(둘 다 나감) DB 가 null 로 바꿈
    @Column(name = "chat_room_id")
    private Long chatRoomId;

    public void reply(String content) {
        this.replyContent = content;
        this.repliedDt = TimeDefaults.nowKst();
        this.status = Status.REPLIED;
    }

    public void toChat(Long roomId) {
        this.chatRoomId = roomId;
        this.status = Status.CHAT;
    }
}
