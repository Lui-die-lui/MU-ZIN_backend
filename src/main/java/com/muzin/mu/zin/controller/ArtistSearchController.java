package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.artist.ArtistProfileDetailResponse;
import com.muzin.mu.zin.dto.artist.ArtistSearchRequest;
import com.muzin.mu.zin.dto.artist.ArtistSearchResponse;
import com.muzin.mu.zin.service.ArtistSearch.ArtistSearchService;
import com.muzin.mu.zin.service.artistProfile.ArtistProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/artists")
public class ArtistSearchController {

    private final ArtistSearchService artistSearchService;
    private final ArtistProfileService artistProfileService;

    @GetMapping
    // /artists?keyword=플룻&instCategory=WOODWIND&instIds=1&instIds=2&styleTagIds=3
    public ResponseEntity<List<ArtistSearchResponse>> searchArtists(
            @ModelAttribute ArtistSearchRequest req
//            @RequestParam(required = false) String keyword,
//            @RequestParam(required = false) InstrumentCategory instCategory,
//            @RequestParam(required = false) List<Long> instIds,
//            @RequestParam(required = false) List<Long> styleTagIds
            ) {
        return ResponseEntity.ok(artistSearchService.searchArtists(req));
    }

    @GetMapping("/{artistProfileId}")
    public ApiRespDto<ArtistProfileDetailResponse> getArtistProfileDetail(
            @PathVariable Long artistProfileId
    ) {
        return artistProfileService.getArtistProfileDetail(artistProfileId);
    }
}
