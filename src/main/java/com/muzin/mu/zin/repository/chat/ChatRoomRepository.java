package com.muzin.mu.zin.repository.chat;

import com.muzin.mu.zin.entity.chat.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByStudent_UserIdAndArtistProfile_ArtistProfileId(Long studentUserId, Long artistProfileId);

    // 내가 수강생이거나 아티스트인 방 (최근 메시지 순, 메시지 없는 방은 만든 순)
    @Query("""
            select r from ChatRoom r
            join fetch r.student s
            join fetch r.artistProfile ap
            join fetch ap.user au
            where s.userId = :userId or au.userId = :userId
            order by coalesce(r.lastMessageDt, r.createDt) desc, r.chatRoomId desc
            """)
    List<ChatRoom> findMyRooms(@Param("userId") Long userId);

    @Query("""
            select r from ChatRoom r
            join fetch r.student
            join fetch r.artistProfile ap
            join fetch ap.user
            where r.chatRoomId = :roomId
            """)
    Optional<ChatRoom> findWithParticipants(@Param("roomId") Long roomId);
}
