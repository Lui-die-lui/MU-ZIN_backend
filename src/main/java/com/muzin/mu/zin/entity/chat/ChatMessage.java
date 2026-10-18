package com.muzin.mu.zin.entity.chat;

import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message", indexes = @Index(name = "idx_chat_message_room", columnList = "chat_room_id, chat_message_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_message_id")
    private Long chatMessageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_user_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "edited_dt")
    private LocalDateTime editedDt;

    @Column(name = "deleted_dt")
    private LocalDateTime deletedDt;

    public boolean isDeleted() {
        return deletedDt != null;
    }

    public void edit(String content, LocalDateTime now) {
        this.content = content;
        this.editedDt = now;
    }

    // 삭제: 내용은 DB 에서도 지움 (복원 불가), 자리만 "삭제된 메시지"로 남김
    public void delete(LocalDateTime now) {
        this.content = "";
        this.deletedDt = now;
    }
}
