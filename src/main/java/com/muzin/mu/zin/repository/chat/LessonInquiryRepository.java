package com.muzin.mu.zin.repository.chat;

import com.muzin.mu.zin.entity.chat.LessonInquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LessonInquiryRepository extends JpaRepository<LessonInquiry, Long> {

    // 같은 아티스트에게 답변을 기다리는 문의 (1개까지만)
    boolean existsByUser_UserIdAndArtistProfile_ArtistProfileIdAndStatus(Long userId, Long artistProfileId, LessonInquiry.Status status);

    @Query("""
            select i from LessonInquiry i join fetch i.user join fetch i.artistProfile ap join fetch ap.user
            where i.user.userId = :userId order by i.inquiryId desc
            """)
    List<LessonInquiry> findSent(@Param("userId") Long userId);

    @Query("""
            select i from LessonInquiry i join fetch i.user join fetch i.artistProfile ap join fetch ap.user
            where ap.user.userId = :artistUserId order by i.inquiryId desc
            """)
    List<LessonInquiry> findReceived(@Param("artistUserId") Long artistUserId);

    long countByArtistProfile_User_UserIdAndStatus(Long artistUserId, LessonInquiry.Status status);
}
