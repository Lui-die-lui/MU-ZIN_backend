package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.region.RegionOptionDto;
import com.muzin.mu.zin.service.RegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/regions")
public class RegionController {

    private final RegionService regionService;

    @GetMapping("/sido")
    public List<RegionOptionDto> getSidoList(
            @RequestParam(required = false) String q
    ) {
        return regionService.getSidoList(q);
    }

    @GetMapping("/children")
    public List<RegionOptionDto> getChildRegions(
            @RequestParam Long parentId,
            @RequestParam(required = false) String q
    ) {
        return regionService.getChildRegions(parentId, q);
    }
}
