package com.muzin.mu.zin.service.report;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.report.ReportCreateRequest;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.admin.ModerationRepository;
import com.muzin.mu.zin.repository.admin.ReportTargetRepository;
import com.muzin.mu.zin.repository.admin.ReportTargetRepository.Target;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/*
 * 유저 신고 - 관리자 화면(이상행동 > 신고)에서 확인 후 정지/무시
 *  - 같은 대상은 한 사람이 한 번만, 하루 10건까지
 *  - 신고 당시 내용을 남겨 둠 (나중에 수정·삭제돼도 관리자가 볼 수 있게)
 *  - 신고 사실은 상대에게 알리지 않음
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    public static final int DAILY_LIMIT = 10;

    private final ModerationRepository moderationRepository;
    private final ReportTargetRepository targetRepository;

    public void create(long reporterId, ReportCreateRequest req) {
        String type = req.targetType();
        long refId = req.targetId();

        Optional<Target> found = switch (type) {
            case "ARTIST" -> targetRepository.artist(refId);
            case "CHAT_ROOM" -> targetRepository.chatRoomCounterpart(refId, reporterId);
            case "REVIEW" -> targetRepository.review(refId);
            case "INQUIRY" -> targetRepository.inquiry(refId, reporterId);
            default -> Optional.empty();
        };
        Target target = found.orElseThrow(() -> ApiException.notFound("신고할 대상을 찾을 수 없습니다."));

        if (target.userId() == reporterId) {
            throw ApiException.badRequest("본인은 신고할 수 없습니다.");
        }
        if (moderationRepository.reportExists(reporterId, type, refId)) {
            throw ApiException.conflict("ALREADY_REPORTED", "이미 신고했어요. 관리자가 확인 중이에요.");
        }
        LocalDateTime now = TimeDefaults.nowKst();
        if (moderationRepository.reportsSince(reporterId, now.minusDays(1)) >= DAILY_LIMIT) {
            throw ApiException.tooManyRequests("REPORT_LIMIT", "신고는 하루 " + DAILY_LIMIT + "건까지 할 수 있어요.", 3600);
        }

        String detail = req.detail() == null || req.detail().isBlank() ? null : req.detail().trim();
        try {
            moderationRepository.insertReport(reporterId, target.userId(), type, refId, req.reason(), detail,
                    target.snapshot(), now);
        } catch (DuplicateKeyException e) {
            // 동시에 두 번 눌린 경우
            throw ApiException.conflict("ALREADY_REPORTED", "이미 신고했어요. 관리자가 확인 중이에요.");
        }
        log.info("신고 접수: type={} ref={} target={} by={}", type, refId, target.userId(), reporterId);
    }
}
