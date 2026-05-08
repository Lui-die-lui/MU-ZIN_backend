package com.muzin.mu.zin.repository.artist;

import com.muzin.mu.zin.entity.ArtistFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtistFavoriteRepository extends JpaRepository<ArtistFavorite, Long> {

    // 유저가 좋아한 아티스트 목록이 해당 아티스트를 포함 유무(토글 표시용)
    boolean existsByUser_UserIdAndArtistProfile_ArtistProfileId(
            Long userId,
            Long artistProfileId
    );

    // 유저가 관심 누른 아티스트 단일
    Optional<ArtistFavorite> findByUser_UserIdAndArtistProfile_ArtistProfileId(
            Long userId,
            Long artistProfileId
    );

    // 유저가 관심 누른 아티스트 목록
    List<ArtistFavorite> findAllByUser_UserIdOrderByCreatedAtDesc(Long userId);

    // 관심 아티스트 등록 수 count
    long countByArtistProfile_ArtistProfileId(Long artistProfileId);
}
