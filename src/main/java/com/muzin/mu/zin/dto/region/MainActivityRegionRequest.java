package com.muzin.mu.zin.dto.region;

import java.math.BigDecimal;

// 아티스트 주 활동 지역 및 스튜디오 주소
public record MainActivityRegionRequest(
        String region1DepthName,
        String region2DepthName,
        String region3DepthName,
        String addressLabel,
        String roadAddress,
        String jibunAddress,
        String detailAddress,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
