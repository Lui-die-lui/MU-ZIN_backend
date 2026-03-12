package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.artist.ArtistSearchRequest;
import com.muzin.mu.zin.dto.artist.ArtistSearchResponse;
import com.muzin.mu.zin.service.ArtistSearch.ArtistSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/artists")
public class ArtistSearchController {

    private final ArtistSearchService artistSearchService;

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
}
