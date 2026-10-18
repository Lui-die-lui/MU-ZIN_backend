package com.muzin.mu.zin.repository.artist;

import com.muzin.mu.zin.dto.artist.ArtistInstrumentRow;
import com.muzin.mu.zin.dto.artist.ArtistSearchRequest;
import com.muzin.mu.zin.dto.artist.ArtistSearchResponse;
import com.muzin.mu.zin.dto.artist.ArtistSearchRow;
import com.muzin.mu.zin.dto.region.SearchServiceRegionRow;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ArtistProfileRepositoryCustom {
    // viewerUserId: 로그인한 사람 (followedOnly 조건에 사용, 비로그인 null)
    List<ArtistSearchRow> searchArtistRows(ArtistSearchRequest req, Pageable pageable, Long viewerUserId);

    // 승인된 아티스트만, 주어진 id 들의 카드 행 (내 팔로잉 목록)
    List<ArtistSearchRow> findArtistRowsByIds(List<Long> artistProfileIds);

    List<ArtistInstrumentRow> findArtistInstrumentRows(List<Long> artistProfileIds);

    List<SearchServiceRegionRow> findArtistServiceRegionRows(List<Long> artistProfileIds);
}
