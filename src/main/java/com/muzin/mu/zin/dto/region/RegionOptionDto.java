package com.muzin.mu.zin.dto.region;

public record RegionOptionDto(
        Long regionId,
        String name,
        String fullName,
        Short depth,
        Long parentRegionId
) {
}
