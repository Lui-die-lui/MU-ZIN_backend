package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.ApiRespDto;
import com.muzin.mu.zin.dto.artist.*;
import com.muzin.mu.zin.service.ArtistSearch.ArtistLessonQueryService;
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
    private final ArtistLessonQueryService artistLessonQueryService;

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

    @GetMapping("/{artistProfileId}/lessons")
    public ApiRespDto<List<ArtistLessonCardResponse>> getArtistLessonCards(
            @PathVariable Long artistProfileId
    ) {
        return artistLessonQueryService.getArtistLessonCards(artistProfileId);
    }

    @GetMapping("/{artistProfileId}/lessons/{lessonId}")
    public ApiRespDto<ArtistLessonDetailResponse> getArtistLessonDetail(
            @PathVariable Long artistProfileId,
            @PathVariable Long lessonId
    ) {
        return artistLessonQueryService.getArtistLessonDetail(artistProfileId, lessonId);
    }
}
