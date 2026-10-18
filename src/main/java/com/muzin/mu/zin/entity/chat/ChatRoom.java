package com.muzin.mu.zin.entity.chat;

import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 수강생 1명 + 아티스트 1명 채팅방 (V5__chat.sql)
@Entity
@Table(name = "chat_room",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_room_pair", columnNames = {"user_id", "artist_profile_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id")
    private Long chatRoomId;

    // 수강생
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_profile_id", nullable = false)
    private ArtistProfile artistProfile;

    // 각자 마지막으로 읽은 메시지 id (안 읽은 수 계산)
    @Column(name = "student_last_read_id")
    private Long studentLastReadId;

    @Column(name = "artist_last_read_id")
    private Long artistLastReadId;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_dt")
    private LocalDateTime lastMessageDt;

    // 나가기 (나간 사람 화면에서 cleared_id 이하 메시지는 영구히 안 보임)
    @Builder.Default
    @Column(name = "student_left", nullable = false)
    private boolean studentLeft = false;

    @Builder.Default
    @Column(name = "artist_left", nullable = false)
    private boolean artistLeft = false;

    @Column(name = "student_cleared_id")
    private Long studentClearedId;

    @Column(name = "artist_cleared_id")
    private Long artistClearedId;

    // 아티스트가 문의를 채팅으로 넘긴 경우 대화 가능 마감
    @Column(name = "artist_open_until")
    private LocalDateTime artistOpenUntil;

    public boolean isStudent(Long userId) {
        return student.getUserId().equals(userId);
    }

    public boolean isArtist(Long userId) {
        return artistProfile.getUser().getUserId().equals(userId);
    }

    public boolean isParticipant(Long userId) {
        return isStudent(userId) || isArtist(userId);
    }

    public Long counterpartUserId(Long userId) {
        return isStudent(userId) ? artistProfile.getUser().getUserId() : student.getUserId();
    }

    public Long lastReadIdOf(Long userId) {
        return isStudent(userId) ? studentLastReadId : artistLastReadId;
    }

    // 읽음은 뒤로 가지 않음
    public void markRead(Long userId, Long messageId) {
        if (messageId == null) return;
        if (isStudent(userId)) {
            if (studentLastReadId == null || messageId > studentLastReadId) studentLastReadId = messageId;
        } else if (isArtist(userId)) {
            if (artistLastReadId == null || messageId > artistLastReadId) artistLastReadId = messageId;
        }
    }

    public boolean hasLeft(Long userId) {
        return isStudent(userId) ? studentLeft : artistLeft;
    }

    public boolean counterpartLeft(Long userId) {
        return isStudent(userId) ? artistLeft : studentLeft;
    }

    public boolean anyoneLeft() {
        return studentLeft || artistLeft;
    }

    public Long clearedIdOf(Long userId) {
        return isStudent(userId) ? studentClearedId : artistClearedId;
    }

    // 나가기: 지금까지의 메시지를 내 화면에서 지움 (복원 불가)
    public void leave(Long userId) {
        if (isStudent(userId)) {
            studentLeft = true;
            studentClearedId = lastMessageId;
        } else if (isArtist(userId)) {
            artistLeft = true;
            artistClearedId = lastMessageId;
        }
    }

    public boolean bothLeft() {
        return studentLeft && artistLeft;
    }

    // 새 예약 수락 / 문의를 채팅으로 넘김 -> 다시 열림 (나갔던 사람에게 예전 메시지는 계속 안 보임)
    public void reopen() {
        studentLeft = false;
        artistLeft = false;
    }

    public void openByArtist(LocalDateTime until) {
        if (artistOpenUntil == null || until.isAfter(artistOpenUntil)) artistOpenUntil = until;
    }

    public void onMessage(ChatMessage message) {
        this.lastMessageId = message.getChatMessageId();
        this.lastMessageDt = message.getCreateDt();
    }
}
