package com.muzin.mu.zin.dto.instrument;

import java.util.List;

public record ArtistInstrumentAddRequest(
        List<Long> instrumentIds
) {
}
