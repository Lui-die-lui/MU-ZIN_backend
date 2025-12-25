package com.muzin.mu.zin.service;

import com.muzin.mu.zin.dto.instrument.InstrumentResponse;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.instrument.Instrument;
import com.muzin.mu.zin.entity.instrument.InstrumentCategory;
import com.muzin.mu.zin.entity.instrument.InstrumentStatus;
import com.muzin.mu.zin.repository.InstrumentRepository;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.security.model.PrincipalUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;
    private final UserRepository userRepository;

    // User가 필터에 없는 악기 등록 요청
    @Transactional
    public InstrumentResponse requestNewInstrument(
            String instName,
            InstrumentCategory category,
            PrincipalUser principalUser
    ) {
        User user = userRepository.findById(principalUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("유저가 없습니다."));

        String name = (instName == null) ? "" : instName.trim().replaceAll("\\s+", " ");
        if (name.isBlank()) throw new IllegalArgumentException("악기명은 필수입니다.");
        if (name.length() > 20) throw new IllegalArgumentException("20자 이하로 입력해주세요.");
        if (category == null) category = InstrumentCategory.ETC;

        // 중복 방지 - 이미 있으면 상태에 따라 안내
        instrumentRepository.findByInstName(name).ifPresent(existing -> {
            switch (existing.getStatus()) {
                case APPROVED -> throw new IllegalStateException("이미 등록되었습니다.");
                case PENDING -> throw new IllegalStateException("이미 검수중입니다.");
                case REJECTED -> throw new IllegalStateException("반려되었습니다. 다른 이름으로 요청해주세요.");
                case INACTIVE -> throw new IllegalStateException("비활성화 되었습니다. 관리자에게 문의해주세요.");
            }
        });

        Instrument pending = Instrument.request(user, name, category);
        Instrument saved = instrumentRepository.save(pending);

        return toResponse(saved);
    }

    // 악기 리스트 조회 시 본인 pending 포함 - 이건 나중에 생각해보기

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
    public List<Instrument> validateAndGetApprovedByIds(List<Long> instIds) {
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
