package com.muzin.mu.zin.dto.instrument;

import com.muzin.mu.zin.entity.instrument.InstrumentCategory;

public record InstrumentResponse(
        Long instId,
        String instName,
        InstrumentCategory category
) {
}
