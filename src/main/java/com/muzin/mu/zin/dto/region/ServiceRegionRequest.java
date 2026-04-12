package com.muzin.mu.zin.dto.region;

// 여러개 선택 가능한 서비스 지역
public record ServiceRegionRequest(
        String region1DepthName,
        String region2DepthName,
        String region3DepthName
) {
}
