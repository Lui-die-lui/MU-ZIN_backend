package com.muzin.mu.zin.controller;

import com.muzin.mu.zin.dto.instrument.InstrumentResponse;
import com.muzin.mu.zin.entity.instrument.InstrumentCategory;
import com.muzin.mu.zin.service.InstrumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;

    /**
     * ✅ 승인된 악기 목록 조회
     *
     * 사용 예)
     * GET /instruments
     * GET /instruments?category=STRING
     * GET /instruments?q=피아노
     * GET /instruments?category=STRING&q=기타
     */

    @GetMapping
    public List<InstrumentResponse> getInstruments(
            @RequestParam(required = false)InstrumentCategory category,
            @RequestParam(required = false, name = "q") String keyword
            ) {
        // category + keyword 둘 다 있으면 category 먼저 걸고 keword로 검색하려면
        // repository 메서드가 추가로 필요해서 MVP에서는 keword 우선으로처리
        // 혹은 아래처럼 분기 정책을 정하기

        if (keyword != null && !keyword.trim().isEmpty()) {
            return instrumentService.searchApprovedInstruments(keyword);
        }

        if (category != null) {
            return instrumentService.getApprovedInstrumentsByCategory(category);
        }

        return instrumentService.getApprovedInstruments();
    }

    // 프론트 Enum 하드코딩 없이 카테고리 표시 가능
    // 굳이 db까지 한번 갔다와야하나 하는 생각이 드는데
    // 보고 프론트에서 해결할 수 있으면 프론트에서 해결하는 방향 생각해보기
    @GetMapping("/categories")
    public InstrumentCategory[] getCategories() {
        return InstrumentCategory.values();
    }
}
