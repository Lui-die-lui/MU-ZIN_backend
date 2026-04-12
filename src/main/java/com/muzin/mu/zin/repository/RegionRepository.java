package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.RegionMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegionRepository extends JpaRepository<RegionMaster, Long> {
    List<RegionMaster> findByDepthAndIsActiveTrueOrderBySortOrderAscNameAsc(short depth);

    List<RegionMaster> findByParentRegion_RegionIdAndIsActiveTrueOrderBySortOrderAscNameAsc(Long parentRegionId);

    List<RegionMaster> findTop20ByDepthAndNameStartingWithAndIsActiveTrueOrderBySortOrderAscNameAsc(short depth, String name);

    List<RegionMaster> findTop20ByParentRegion_RegionIdAndNameStartingWithAndIsActiveTrueOrderBySortOrderAscNameAsc(
            Long parentRegionId,
            String name
    );
}
