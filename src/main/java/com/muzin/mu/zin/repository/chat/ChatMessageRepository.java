package com.muzin.mu.zin.repository.chat;

import com.muzin.mu.zin.entity.chat.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 최신부터 거꾸로 (beforeId 보다 이전), 화면에서 다시 시간순으로 뒤집음
    @Query("""
            select m from ChatMessage m join fetch m.sender
            where m.chatRoom.chatRoomId = :roomId and (:beforeId is null or m.chatMessageId < :beforeId)
              and (:clearedId is null or m.chatMessageId > :clearedId)
            order by m.chatMessageId desc
            """)
    List<ChatMessage> findPage(@Param("roomId") Long roomId, @Param("beforeId") Long beforeId,
                               @Param("clearedId") Long clearedId, Pageable pageable);

    // 상대가 보낸, 내가 아직 안 읽은 메시지 수
    @Query("""
            select count(m) from ChatMessage m
            where m.chatRoom.chatRoomId = :roomId and m.sender.userId <> :userId
              and (:lastReadId is null or m.chatMessageId > :lastReadId)
            """)
    long countUnread(@Param("roomId") Long roomId, @Param("userId") Long userId, @Param("lastReadId") Long lastReadId);

    // 둘 다 나간 방 정리
    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from ChatMessage m where m.chatRoom.chatRoomId = :roomId")
    void deleteAllInRoom(@Param("roomId") Long roomId);
}
