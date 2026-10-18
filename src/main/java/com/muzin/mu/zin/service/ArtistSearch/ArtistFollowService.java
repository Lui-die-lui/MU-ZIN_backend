package com.muzin.mu.zin.service.ArtistSearch;

import com.muzin.mu.zin.dto.artist.ArtistFollowResponse;
import com.muzin.mu.zin.dto.artist.ArtistSearchResponse;
import com.muzin.mu.zin.entity.ArtistFavorite;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.ArtistStatus;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.repository.artist.ArtistFavoriteRepository;
import com.muzin.mu.zin.repository.artist.ArtistProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/*
 * 아티스트 팔로우 (즐겨찾기처럼 사용, 새 레슨 알림 없음)
 *  - 승인된 아티스트만, 내 아티스트 프로필은 팔로우 불가
 *  - 팔로우/취소는 여러 번 눌러도 결과가 같음 (이미 팔로우 중이면 그대로)
 *  - 공개: 팔로워 수만. 누가 팔로우했는지는 아티스트 본인에게도 보여주지 않음
 *  - 내 팔로잉 목록은 나만 볼 수 있음
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistFollowService {

    private final ArtistFavoriteRepository favoriteRepository;
    private final ArtistProfileRepository artistProfileRepository;
    private final UserRepository userRepository;
    private final ArtistSearchService artistSearchService;

    // 팔로워 수 + (로그인했다면) 내가 팔로우 중인지
    public ArtistFollowResponse getStatus(Long artistProfileId, Long viewerUserId) {
        return status(findApprovedArtist(artistProfileId), viewerUserId);
    }

    @Transactional
    public ArtistFollowResponse follow(Long artistProfileId, Long userId) {
        ArtistProfile artist = findApprovedArtist(artistProfileId);
        if (Objects.equals(artist.getUser().getUserId(), userId)) {
            throw ApiException.badRequest("내 프로필은 팔로우할 수 없어요.");
        }
        if (!favoriteRepository.existsByUser_UserIdAndArtistProfile_ArtistProfileId(userId, artistProfileId)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없어요."));
            favoriteRepository.save(ArtistFavorite.builder().user(user).artistProfile(artist).build());
        }
        return status(artist, userId);
    }

    @Transactional
    public ArtistFollowResponse unfollow(Long artistProfileId, Long userId) {
        // 승인이 취소된 아티스트도 팔로우는 풀 수 있게 (존재만 확인)
        ArtistProfile artist = artistProfileRepository.findById(artistProfileId)
                .orElseThrow(() -> ApiException.notFound("아티스트를 찾을 수 없어요."));
        favoriteRepository.findByUser_UserIdAndArtistProfile_ArtistProfileId(userId, artistProfileId)
                .ifPresent(favoriteRepository::delete);
        favoriteRepository.flush();
        return status(artist, userId);
    }

    // 내가 팔로우한 아티스트 (최근 팔로우 순, 검색 카드와 같은 모양)
    public List<ArtistSearchResponse> getMyFollowings(Long userId) {
        List<Long> ids = favoriteRepository.findAllByUser_UserIdOrderByCreatedAtDesc(userId).stream()
                .map(f -> f.getArtistProfile().getArtistProfileId())
                .toList();
        return artistSearchService.findCardsByIdsInOrder(ids, userId);
    }

    private ArtistFollowResponse status(ArtistProfile artist, Long viewerUserId) {
        Long artistProfileId = artist.getArtistProfileId();
        long followers = favoriteRepository.countByArtistProfile_ArtistProfileId(artistProfileId);
        boolean following = viewerUserId != null
                && favoriteRepository.existsByUser_UserIdAndArtistProfile_ArtistProfileId(viewerUserId, artistProfileId);
        return new ArtistFollowResponse(artistProfileId, followers, following);
    }

    private ArtistProfile findApprovedArtist(Long artistProfileId) {
        ArtistProfile artist = artistProfileRepository.findById(artistProfileId)
                .orElseThrow(() -> ApiException.notFound("아티스트를 찾을 수 없어요."));
        if (artist.getUser().getArtistStatus() != ArtistStatus.APPROVED) {
            throw ApiException.notFound("아티스트를 찾을 수 없어요.");
        }
        return artist;
    }
}
