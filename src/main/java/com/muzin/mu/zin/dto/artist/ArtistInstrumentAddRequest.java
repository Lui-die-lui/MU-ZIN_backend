package com.muzin.mu.zin.dto.artist;

import java.util.List;

public record ArtistInstrumentAddRequest(
        List<Long> instrumentIds
) {
}
