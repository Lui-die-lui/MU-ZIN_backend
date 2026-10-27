package com.muzin.mu.zin.service.adminService;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.repository.admin.ModerationRepository;
import com.muzin.mu.zin.repository.admin.ModerationRepository.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/*
 * 이상행동 감지 - 10분마다 최근 기록을 세서 기준을 넘은 유저를 관리자 화면(이상행동)에 띄움
 *  - 자동으로 막지는 않음. 관리자가 근거를 보고 정지하거나 무시
 *  - 같은 유저·같은 규칙은 열린 건 하나만 (다시 걸리면 수치만 갱신)
 *  - 관리자가 무시한 건은 7일 동안 다시 띄우지 않음
 *  - 관리자·샘플·이미 정지된 계정은 제외
 *
 * 시각 기준: 채팅·문의·리뷰의 create_dt 는 JPA 자동 기록(서버 기본 시간대)이라 LocalDateTime.now(),
 *           예약 요청·취소 시각과 새 테이블은 한국 시간(TimeDefaults.nowKst)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbuseDetectionService {

    // 기준값 - 실제 사용 패턴을 보면서 조정
    static final int CHAT_MESSAGES_PER_HOUR = 80;
    static final int CHAT_ROOMS_PER_HOUR = 8;
    static final int INQUIRY_ARTISTS_PER_DAY = 5;
    static final int INQUIRY_SAME_CONTENT = 3;
    static final int RESERVATION_REQUESTS_PER_DAY = 8;
    static final int USER_CANCELS_PER_WEEK = 4;
    static final int LOGIN_FAILURES_PER_DAY = 10;
    static final int REVIEWS_PER_DAY = 4;
    static final int ONE_STAR_ARTISTS_PER_MONTH = 3;
    static final int REPORTERS = 3;
    static final int DISMISS_SUPPRESS_DAYS = 7;
    static final int LOGIN_FAILURE_KEEP_DAYS = 30;

    private record Rule(String code, List<Hit> hits, Function<Hit, String> detail) { }

    private final ModerationRepository moderationRepository;

    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 60 * 1000)
    @Transactional
    public void detect() {
        LocalDateTime audit = LocalDateTime.now();
        LocalDateTime kst = TimeDefaults.nowKst();

        List<Rule> rules = List.of(
                new Rule("CHAT_FLOOD",
                        moderationRepository.chatFlood(audit.minusHours(1), CHAT_MESSAGES_PER_HOUR, CHAT_ROOMS_PER_HOUR),
                        h -> "최근 1시간 동안 대화방 " + h.extra() + "곳에 메시지 " + h.metric() + "개"),
                new Rule("INQUIRY_SPAM",
                        moderationRepository.inquirySpam(audit.minusDays(1), INQUIRY_ARTISTS_PER_DAY, INQUIRY_SAME_CONTENT),
                        h -> "최근 24시간 동안 아티스트 " + h.metric() + "명에게 문의"
                                + (h.extra() >= INQUIRY_SAME_CONTENT ? " (같은 내용을 " + h.extra() + "명에게)" : "")),
                new Rule("RESERVATION_SPAM",
                        moderationRepository.reservationSpam(kst.minusDays(1), RESERVATION_REQUESTS_PER_DAY),
                        h -> "최근 24시간 동안 예약 요청 " + h.metric() + "건 (레슨 " + h.extra() + "개)"),
                new Rule("RESERVATION_CANCEL",
                        moderationRepository.reservationCancel(kst.minusDays(7), USER_CANCELS_PER_WEEK),
                        h -> "최근 7일 동안 직접 취소한 예약 " + h.metric() + "건"),
                new Rule("LOGIN_FAILURE",
                        moderationRepository.loginFailure(kst.minusDays(1), LOGIN_FAILURES_PER_DAY),
                        h -> "최근 24시간 동안 이 계정으로 로그인 실패 " + h.metric() + "회 (비밀번호 대입 시도 의심)"),
                new Rule("REVIEW_BURST",
                        moderationRepository.reviewBurst(audit.minusDays(1), REVIEWS_PER_DAY),
                        h -> "최근 24시간 동안 리뷰 " + h.metric() + "개 (아티스트 " + h.extra() + "명)"),
                new Rule("REVIEW_LOW_RATING",
                        moderationRepository.reviewLowRating(audit.minusDays(30), ONE_STAR_ARTISTS_PER_MONTH),
                        h -> "최근 30일 동안 아티스트 " + h.metric() + "명에게 별점 1점 (리뷰 " + h.extra() + "개)"),
                new Rule("REPORTED",
                        moderationRepository.reported(REPORTERS),
                        h -> "처리 안 된 신고 " + h.extra() + "건 (신고한 사람 " + h.metric() + "명)")
        );

        LocalDateTime suppressSince = kst.minusDays(DISMISS_SUPPRESS_DAYS);
        int created = 0;
        for (Rule rule : rules) {
            for (Hit h : rule.hits()) {
                if (moderationRepository.upsertFlag(h.userId(), rule.code(), rule.detail().apply(h), h.metric(), kst, suppressSince)) {
                    created++;
                    log.info("이상행동 감지: rule={} user={} metric={}", rule.code(), h.userId(), h.metric());
                }
            }
        }
        if (created > 0) log.info("이상행동 새로 감지 {}건", created);
    }

    // 로그인 실패 기록은 30일만 보관
    @Scheduled(cron = "0 40 4 * * *", zone = TimeDefaults.DEFAULT_TZ)
    @Transactional
    public void cleanup() {
        int n = moderationRepository.deleteLoginFailuresBefore(TimeDefaults.nowKst().minusDays(LOGIN_FAILURE_KEEP_DAYS));
        if (n > 0) log.info("오래된 로그인 실패 기록 {}건 삭제", n);
    }
}
