package com.muzin.mu.zin.service.ArtistSearch;

import com.muzin.mu.zin.dto.artist.*;
import com.muzin.mu.zin.repository.artist.ArtistProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArtistSearchService {

    private final ArtistProfileRepository artistProfileRepository;


    public List<ArtistSearchResponse> searchArtists(ArtistSearchRequest req) {
        List<ArtistSearchRow> artistRows = artistProfileRepository.searchArtistRows(req);

        // 검색 조건에 부합한 아티스트를 stream/map 을 이용해 리스트로 만듦
        List<Long> artistProfileIds = artistRows.stream()
                .map(ArtistSearchRow::artistProfileId)
                .toList();

        // 그 아티스트의 레슨 가능 악기 리스트
        List<ArtistInstrumentRow> instrumentRows =
                artistProfileRepository.findArtistInstrumentRows(artistProfileIds);

        // 해당 부분 다시 보기
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

        return artistRows.stream()
                .map(row -> new ArtistSearchResponse(
                        row.artistProfileId(),
                        row.username(),
                        row.majorName(),
                        row.email(),
                        row.profileImgUrl(),
                        instrumentMap.getOrDefault(row.artistProfileId(), List.of())
                ))
                .toList();
    }
}
