package com.muzin.mu.zin.service.ArtistSearch;

import com.muzin.mu.zin.dto.artist.*;
import com.muzin.mu.zin.dto.region.SearchMainRegionSummary;
import com.muzin.mu.zin.dto.region.SearchServiceRegionRow;
import com.muzin.mu.zin.dto.region.ServiceRegionResponse;
import com.muzin.mu.zin.repository.artist.ArtistProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArtistSearchService {

    private final ArtistProfileRepository artistProfileRepository;


    @Transactional(readOnly = true)
    public List<ArtistSearchResponse> searchArtists(ArtistSearchRequest req) {
        List<ArtistSearchRow> artistRows = artistProfileRepository.searchArtistRows(req);

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

        return artistRows.stream()
                .map(row -> new ArtistSearchResponse(
                        row.artistProfileId(),
                        row.username(),
                        row.majorName(),
                        row.email(),
                        row.profileImgUrl(),
                        new SearchMainRegionSummary(
                                row.region1DepthName(),
                                row.region2DepthName(),
                                row.region3DepthName(),
                                row.addressLabel()
                        ),
                        serviceRegionMap.getOrDefault(row.artistProfileId(), List.of()),
                        instrumentMap.getOrDefault(row.artistProfileId(), List.of())
                ))
                .toList();
    }
}
