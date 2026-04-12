package com.muzin.mu.zin.service;

import com.muzin.mu.zin.dto.region.RegionOptionDto;
import com.muzin.mu.zin.entity.RegionMaster;
import com.muzin.mu.zin.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {

    private final RegionRepository regionRepository;

    // 가장 상위(시/도 리스트 받아오기)
    public List<RegionOptionDto> getSidoList(String q) {
        String keyword = normalizeKeyword(q);

        List<RegionMaster> regions = keyword == null
                ? regionRepository.findByDepthAndIsActiveTrueOrderBySortOrderAscNameAsc((short) 1)
                : regionRepository.findTop20ByDepthAndNameStartingWithAndIsActiveTrueOrderBySortOrderAscNameAsc(
                        (short) 1,
                        keyword
                );

        return regions.stream()
                .map(this::toDto)
                .toList();
    }

    public List<RegionOptionDto> getChildRegions(Long parentRegionId, String q) {
        if (parentRegionId == null) {
            throw new IllegalArgumentException("상위 지역 ID는 필수입니다.");
        }

        String keyword = normalizeKeyword(q);

        List<RegionMaster> regions = keyword == null
                ? regionRepository.findByParentRegion_RegionIdAndIsActiveTrueOrderBySortOrderAscNameAsc(parentRegionId)
                : regionRepository.findTop20ByParentRegion_RegionIdAndNameStartingWithAndIsActiveTrueOrderBySortOrderAscNameAsc(
                        parentRegionId, q
                );

        return regions.stream()
                .map(this::toDto)
                .toList();
    }



    // 키워드 변환 메서드(사용자 직접 입력 상황 고려)
    private String normalizeKeyword(String q) {
        if (q == null) return null;
        String trimmed = q.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // dto 변환 메서드
    private RegionOptionDto toDto(RegionMaster region) {
        return new RegionOptionDto(
                region.getRegionId(),
                region.getName(),
                region.getFullName(),
                region.getDepth(),
                region.getParentRegion() != null ? region.getParentRegion().getRegionId() : null
        );
    }
}
