package com.muzin.mu.zin.service.ArtistSearch;

import com.muzin.mu.zin.dto.artist.*;
import com.muzin.mu.zin.dto.region.SearchMainRegionSummary;
import com.muzin.mu.zin.dto.region.SearchServiceRegionRow;
import com.muzin.mu.zin.dto.region.ServiceRegionResponse;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.artist.ArtistFavoriteRepository;
import com.muzin.mu.zin.repository.artist.ArtistProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArtistSearchService {

    private final ArtistProfileRepository artistProfileRepository;
    private final ArtistFavoriteRepository favoriteRepository;

    // viewerUserId: 로그인한 사람 (비로그인 null) - 팔로우 여부 표시 / "팔로우한 아티스트만" 조건
    @Transactional(readOnly = true)
    public List<ArtistSearchResponse> searchArtists(ArtistSearchRequest req, Long viewerUserId) {
        if (Boolean.TRUE.equals(req.followedOnly()) && viewerUserId == null) {
            throw ApiException.unauthorized("LOGIN_REQUIRED", "로그인하면 팔로우한 아티스트만 볼 수 있어요.");
        }

        int page = req.page() == null ? 0 : Math.max(req.page(), 0);
        int size = req.size() == null ? 200 : Math.min(Math.max(req.size(), 1), 100);
        Pageable pageable = PageRequest.of(page, size);

        return toResponses(artistProfileRepository.searchArtistRows(req, pageable, viewerUserId), viewerUserId);
    }

    // 주어진 순서대로 카드 (내 팔로잉 목록 - 최근 팔로우 순). 승인이 취소된 아티스트는 빠짐
    @Transactional(readOnly = true)
    public List<ArtistSearchResponse> findCardsByIdsInOrder(List<Long> artistProfileIds, Long viewerUserId) {
        Map<Long, ArtistSearchResponse> byId = toResponses(artistProfileRepository.findArtistRowsByIds(artistProfileIds), viewerUserId)
                .stream()
                .collect(Collectors.toMap(ArtistSearchResponse::artistProfileId, Function.identity()));
        return artistProfileIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private List<ArtistSearchResponse> toResponses(List<ArtistSearchRow> artistRows, Long viewerUserId) {
        if (artistRows.isEmpty()) {
            return List.of();
        }

        // 검색 조건에 부합한 아티스트를 stream/map 을 이용해 리스트로 만듦
        List<Long> artistProfileIds = artistRows.stream()
                .map(ArtistSearchRow::artistProfileId)
                .toList();

        // 그 아티스트의 레슨 가능 악기 리스트
        List<ArtistInstrumentRow> instrumentRows =
                artistProfileRepository.findArtistInstrumentRows(artistProfileIds);

        // 아티스트 서비스 가능 지역 리스트
        List<SearchServiceRegionRow> serviceRegionRows =
                artistProfileRepository.findArtistServiceRegionRows(artistProfileIds);

        // 악기 리스트 매핑
        Map<Long, List<ArtistInstrumentSummary>> instrumentMap = instrumentRows.stream()
                .collect(Collectors.groupingBy(
                        // artistProfileId 기준으로 그룹핑
                        ArtistInstrumentRow::artistProfileId,
                        // 각 row를 instId, instName 으로 바꾸고 List로 모아줌
                        Collectors.mapping(
                                row -> new ArtistInstrumentSummary(row.instId(), row.instName()),
                                Collectors.toList()
                        )
                ));

        // 서비스 가능 지역 리스트 매핑
        Map<Long, List<ServiceRegionResponse>> serviceRegionMap = serviceRegionRows.stream()
                .collect(Collectors.groupingBy(
                        SearchServiceRegionRow::artistProfileId,
                        Collectors.mapping(
                                row -> new ServiceRegionResponse(
                                        row.region1DepthName(),
                                        row.region2DepthName(),
                                        row.region3DepthName()
                                ),
                                Collectors.toList()
                        )
                ));

        // 팔로워 수 / 내가 팔로우한 아티스트 (아티스트 수와 관계없이 쿼리 1번씩)
        Map<Long, Long> followerCounts = new HashMap<>();
        favoriteRepository.countGroupByArtist(artistProfileIds)
                .forEach(row -> followerCounts.put((Long) row[0], (Long) row[1]));
        Set<Long> followed = viewerUserId == null
                ? Set.of()
                : new HashSet<>(favoriteRepository.findFollowedIds(viewerUserId, artistProfileIds));

        return artistRows.stream()
                .map(row -> new ArtistSearchResponse(
                        row.artistProfileId(),
                        row.username(),
                        row.majorName(),
                        row.profileImgUrl(),
                        new SearchMainRegionSummary(
                                row.region1DepthName(),
                                row.region2DepthName(),
                                row.region3DepthName(),
                                row.addressLabel()
                        ),
                        serviceRegionMap.getOrDefault(row.artistProfileId(), List.of()),
                        instrumentMap.getOrDefault(row.artistProfileId(), List.of()),
                        followerCounts.getOrDefault(row.artistProfileId(), 0L),
                        followed.contains(row.artistProfileId())
                ))
                .toList();
    }
}
