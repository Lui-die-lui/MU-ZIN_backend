package com.muzin.mu.zin.dto.region;

import java.math.BigDecimal;

public record MainRegionResponse(
        String region1DepthName,
        String region2DepthName,
        String region3DepthName,
        String addressLabel,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
