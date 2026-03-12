package com.muzin.mu.zin.repository.artist;

import com.muzin.mu.zin.dto.artist.ArtistInstrumentRow;
import com.muzin.mu.zin.dto.artist.ArtistSearchRequest;
import com.muzin.mu.zin.dto.artist.ArtistSearchResponse;
import com.muzin.mu.zin.dto.artist.ArtistSearchRow;

import java.util.List;

public interface ArtistProfileRepositoryCustom {
    List<ArtistSearchRow> searchArtistRows(ArtistSearchRequest req);

    List<ArtistInstrumentRow> findArtistInstrumentRows(List<Long> artistProfileIds);
}
