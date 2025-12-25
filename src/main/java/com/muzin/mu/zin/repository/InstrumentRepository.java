package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.instrument.Instrument;
import com.muzin.mu.zin.entity.instrument.InstrumentCategory;
import com.muzin.mu.zin.entity.instrument.InstrumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

    // 필터 UI용 : 승인된 악기만 노출
    List<Instrument> findAllByStatusOrderByInstNameAsc(InstrumentStatus status);

    // 부분 검색 가능하게 만들어줌
    List<Instrument> findAllByStatusAndInstNameContainingOrderByInstNameAsc(
            InstrumentStatus status, String keyword
    );

    List<Instrument> findAllByStatusAndCategoryOrderByInstNameAsc(
            InstrumentStatus status, InstrumentCategory category
    );

    boolean existsByInstName(String instName);

    Optional<Instrument> findByInstName(String instName);

    // 관리자 검수용
    List<Instrument> findAllByStatusOrderByCreateDtAsc(InstrumentStatus status);

    //
}
