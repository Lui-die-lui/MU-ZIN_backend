package com.muzin.mu.zin.dto.artist;

import java.util.List;

public record ArtistStyleSetRequest(
        List<Long> styleTagIds
) {
}
