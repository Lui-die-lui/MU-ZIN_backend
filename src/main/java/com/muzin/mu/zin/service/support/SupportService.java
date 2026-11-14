package com.muzin.mu.zin.service.support;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.support.AdminSupportItem;
import com.muzin.mu.zin.dto.support.SupportResponse;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.notification.NotificationRefType;
import com.muzin.mu.zin.entity.notification.NotificationType;
import com.muzin.mu.zin.entity.support.SupportInquiry;
import com.muzin.mu.zin.event.NotificationEvent;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.repository.UserRoleRepository;
import com.muzin.mu.zin.repository.support.SupportInquiryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/*
 * 운영자 문의 (로그인 사용자 -> 관리자)
 *  - 질문 1개 + 관리자 답변 1개. 더 물을 게 있으면 새 문의
 *  - 하루 DAILY_LIMIT 개까지 (스팸 방지)
 *  - 알림: 새 문의 -> 관리자 전원 / 답변 -> 문의한 사람 (종 알림 + 푸시)
 *  - 정지된 계정은 로그인이 막혀 여기로 못 옴 -> 푸터 문의 이메일
 *  - 보관: 답변 후 6개월이 지나면 매일 새벽 삭제, 탈퇴하면 바로 삭제 (WithdrawalService)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportService {

    static final int DAILY_LIMIT = 5;
    private static final int CONTENT_MAX = 2000;
    static final int RETENTION_MONTHS = 6; // 답변 후(답변이 없으면 보낸 뒤) 보관 기간

    private final SupportInquiryRepository supportRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public SupportResponse create(Long userId, String rawCategory, String rawContent) {
        SupportInquiry.Category category = parseCategory(rawCategory);
        String content = normalize(rawContent, "문의 내용을 입력해주세요.");

        long today = supportRepository.countByUser_UserIdAndCreateDtGreaterThanEqual(userId, TimeDefaults.startOfTodayKst());
        if (today >= DAILY_LIMIT) {
            long untilTomorrow = Duration.between(TimeDefaults.nowKst(), TimeDefaults.startOfTodayKst().plusDays(1)).toSeconds();
            throw ApiException.tooManyRequests("SUPPORT_DAILY_LIMIT",
                    "운영자 문의는 하루 " + DAILY_LIMIT + "개까지 보낼 수 있어요. 내일 다시 보내주세요.", Math.max(1, untilTomorrow));
        }

        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없어요."));
        SupportInquiry saved = supportRepository.save(SupportInquiry.builder().user(user).category(category).content(content).build());

        userRoleRepository.findActiveAdminUserIds().stream()
                .filter(adminId -> !Objects.equals(adminId, userId))
                .distinct()
                .forEach(adminId -> publisher.publishEvent(new NotificationEvent(adminId, NotificationType.SUPPORT_CREATED,
                        "새 운영자 문의", limit(user.getDisplayName() + "님이 운영자에게 문의했어요."),
                        NotificationRefType.SUPPORT, saved.getSupportId())));
        return toResponse(saved);
    }

    public List<SupportResponse> getMine(Long userId) {
        return supportRepository.findByUser_UserIdOrderBySupportIdDesc(userId).stream().map(SupportService::toResponse).toList();
    }

    // 관리자: 답변 대기(open=true)는 오래된 것부터, 답변 완료는 최근 답변부터
    public Page<AdminSupportItem> adminList(boolean open, Pageable pageable) {
        Sort sort = open ? Sort.by("supportId").ascending() : Sort.by("repliedDt").descending();
        Pageable p = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        return supportRepository.findByStatus(open ? SupportInquiry.Status.OPEN : SupportInquiry.Status.ANSWERED, p)
                .map(SupportService::toAdminItem);
    }

    public long countOpen() {
        return supportRepository.countByStatus(SupportInquiry.Status.OPEN);
    }

    @Transactional
    public AdminSupportItem reply(Long adminId, Long supportId, String rawContent) {
        SupportInquiry inquiry = supportRepository.findById(supportId).orElseThrow(() -> ApiException.notFound("문의를 찾을 수 없어요."));
        if (inquiry.getStatus() != SupportInquiry.Status.OPEN) {
            throw ApiException.conflict("SUPPORT_ALREADY_ANSWERED", "이미 답변한 문의예요.");
        }
        User admin = userRepository.findById(adminId).orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없어요."));
        inquiry.answer(admin, normalize(rawContent, "답변 내용을 입력해주세요."));

        publisher.publishEvent(new NotificationEvent(inquiry.getUser().getUserId(), NotificationType.SUPPORT_REPLIED,
                "운영자 문의 답변", "운영자가 문의에 답변했어요.", NotificationRefType.SUPPORT, inquiry.getSupportId()));
        log.info("운영자 문의 답변: support={} by={}", supportId, adminId);
        return toAdminItem(inquiry);
    }

    // 매일 04:30 (KST) 보관 기간이 지난 문의 삭제 - 개인정보 처리방침의 "답변 후 6개월"과 같은 값
    @Scheduled(cron = "0 30 4 * * *", zone = TimeDefaults.DEFAULT_TZ)
    @Transactional
    public void deleteExpired() {
        int n = supportRepository.deleteExpired(TimeDefaults.nowKst().minusMonths(RETENTION_MONTHS));
        if (n > 0) log.info("운영자 문의 보관 기간 정리: {}건 삭제", n);
    }

    private static SupportInquiry.Category parseCategory(String raw) {
        try {
            return SupportInquiry.Category.valueOf(raw == null ? "" : raw.strip());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("문의 유형을 골라주세요.");
        }
    }

    private static String normalize(String raw, String emptyMessage) {
        String v = raw == null ? "" : raw.strip();
        if (v.isEmpty()) throw ApiException.badRequest(emptyMessage);
        if (v.length() > CONTENT_MAX) throw ApiException.badRequest(CONTENT_MAX + "자까지 보낼 수 있어요.");
        return v;
    }

    private static String limit(String text) {
        return text.length() <= 255 ? text : text.substring(0, 254) + "…";
    }

    private static SupportResponse toResponse(SupportInquiry s) {
        return new SupportResponse(s.getSupportId(), s.getCategory().name(), s.getContent(), s.getStatus().name(),
                s.getReplyContent(), s.getRepliedDt(), s.getCreateDt());
    }

    private static AdminSupportItem toAdminItem(SupportInquiry s) {
        User u = s.getUser();
        return new AdminSupportItem(s.getSupportId(), u.getUserId(), u.getDisplayName(), u.isWithdrawn() ? null : u.getEmail(),
                u.isSuspended(), s.getCategory().name(), s.getContent(), s.getStatus().name(), s.getReplyContent(),
                s.getRepliedBy() == null ? null : s.getRepliedBy().getDisplayName(), s.getRepliedDt(), s.getCreateDt());
    }
}
