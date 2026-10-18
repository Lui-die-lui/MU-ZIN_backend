package com.muzin.mu.zin.repository.artist;

import com.muzin.mu.zin.entity.ArtistFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// 아티스트 팔로우 (= artist_favorite, 즐겨찾기처럼 사용)
// 누가 팔로우했는지 목록을 돌려주는 조회는 일부러 두지 않음 (본인 팔로잉 목록과 개수만)
public interface ArtistFavoriteRepository extends JpaRepository<ArtistFavorite, Long> {

    boolean existsByUser_UserIdAndArtistProfile_ArtistProfileId(Long userId, Long artistProfileId);

    Optional<ArtistFavorite> findByUser_UserIdAndArtistProfile_ArtistProfileId(Long userId, Long artistProfileId);

    // 내가 팔로우한 아티스트 (최근 팔로우 순)
    List<ArtistFavorite> findAllByUser_UserIdOrderByCreatedAtDesc(Long userId);

    long countByArtistProfile_ArtistProfileId(Long artistProfileId);

    // 여러 아티스트의 팔로워 수 한 번에 -> [artistProfileId, count]
    @Query("""
            select f.artistProfile.artistProfileId, count(f) from ArtistFavorite f
            where f.artistProfile.artistProfileId in :ids
            group by f.artistProfile.artistProfileId
            """)
    List<Object[]> countGroupByArtist(@Param("ids") Collection<Long> artistProfileIds);

    // 주어진 아티스트 중 내가 팔로우한 것
    @Query("""
            select f.artistProfile.artistProfileId from ArtistFavorite f
            where f.user.userId = :userId and f.artistProfile.artistProfileId in :ids
            """)
    List<Long> findFollowedIds(@Param("userId") Long userId, @Param("ids") Collection<Long> artistProfileIds);
}
