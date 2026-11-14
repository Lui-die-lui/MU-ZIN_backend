package com.muzin.mu.zin.repository.support;

import com.muzin.mu.zin.entity.support.SupportInquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SupportInquiryRepository extends JpaRepository<SupportInquiry, Long> {

    List<SupportInquiry> findByUser_UserIdOrderBySupportIdDesc(Long userId);

    // 하루 보낼 수 있는 수 확인 (스팸 방지)
    long countByUser_UserIdAndCreateDtGreaterThanEqual(Long userId, LocalDateTime since);

    // 관리자 목록: 답변 대기는 오래된 것부터, 답변 완료는 최근 것부터 (정렬은 서비스에서 Pageable 로)
    @EntityGraph(attributePaths = {"user", "repliedBy"})
    Page<SupportInquiry> findByStatus(SupportInquiry.Status status, Pageable pageable);

    long countByStatus(SupportInquiry.Status status);

    // 보관 기간 정리: 답변 후(답변이 없으면 보낸 뒤) cutoff 이전인 문의
    @Modifying
    @Query("delete from SupportInquiry s where coalesce(s.repliedDt, s.createDt) < :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
