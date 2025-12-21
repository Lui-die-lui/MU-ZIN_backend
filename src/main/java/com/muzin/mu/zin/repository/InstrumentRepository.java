package com.muzin.mu.zin.repository;

import com.muzin.mu.zin.entity.Instrument;
import com.muzin.mu.zin.entity.InstrumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

    // status가 파라미터로 받은 값인 Instrument만 전부 가져옴
    // 그 결과를 instName 기준으로 오름차순
    // 악기 목록을 화면에 보여줄 때 정렬된 리스트를 위해
    List<Instrument> findAllByStatusOrderByInstNameAsc (InstrumentStatus status);
}
