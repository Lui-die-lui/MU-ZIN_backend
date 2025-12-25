package com.muzin.mu.zin.service;

import com.muzin.mu.zin.dto.instrument.InstrumentResponse;
import com.muzin.mu.zin.entity.instrument.Instrument;
import com.muzin.mu.zin.entity.instrument.InstrumentCategory;
import com.muzin.mu.zin.entity.instrument.InstrumentStatus;
import com.muzin.mu.zin.repository.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    // 필터 UI용 - 승인 상태인 악기 전체 조회
    public List<InstrumentResponse> getApprovedInstruments() {
        return instrumentRepository
                .findAllByStatusOrderByInstNameAsc(InstrumentStatus.APPROVED)
                .stream()
                // List<Instrument> 같은 컬렉션을 줄(스트림)처럼 흘려보내며서 가공 할 수 있게 만듬
                // 이 리스트를 하나씩 꺼내서 처리 - 그 다음에 map, filter, sorted 같은걸 붙여서 가공
                .map(this::toResponse) // :: = 메서드 참조 ref 해당 메서드를 여기서 함수처럼 사용해라
                // .map(instrument -> this.toResponse(instrument)) -> 동일한 의미
                .toList();
    }

    // 검색용 : 승인된 악기 + 부분 검색(Containing)
    public List<InstrumentResponse> searchApprovedInstruments(String keyword) {
        String safeKeyword = (keyword == null) ? "" : keyword.trim();

        return instrumentRepository
                .findAllByStatusAndInstNameContainingOrderByInstNameAsc(
                        InstrumentStatus.APPROVED, safeKeyword
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 카테고리 필터용 : 승인된 악기 + 카테고리
    public List<InstrumentResponse> getApprovedInstrumentsByCategory(InstrumentCategory category) {
        return instrumentRepository
                .findAllByStatusAndCategoryOrderByInstNameAsc(
                        InstrumentStatus.APPROVED, category
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 아티스트 악기 매핑에서 instId 목록 검증/조회 시 유용
    public List<Instrument> getApprovedInstrumentEntitiesByIds(List<Long> instIds) {
        if (instIds == null || instIds.isEmpty()) return List.of();

        List<Long> distinctIds = instIds.stream().distinct().toList();
        List<Instrument> instruments = instrumentRepository.findAllById(distinctIds);

        if (instruments.size() != distinctIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 악기가 포함되어 있습니다.");
        }

        boolean hasNotApproved = instruments.stream()
                // 목록 중 하나라도 APPROVED가 아닌게 있으면 걸러야함
                .anyMatch(i -> i.getStatus() != InstrumentStatus.APPROVED);

        if (hasNotApproved) {
            throw new IllegalArgumentException("승인되지 않은 악기가 포함되어 있습니다.");
        }

        return instruments;
    }

    private InstrumentResponse toResponse(Instrument instrument) {
        return new InstrumentResponse(
                instrument.getInstId(),
                instrument.getInstName()
        );
    }
}
