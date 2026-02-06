package com.muzin.mu.zin.service.notification;

import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.notification.Notification;
import com.muzin.mu.zin.event.NotificationEvent;
import com.muzin.mu.zin.repository.NotificationRepository;
import com.muzin.mu.zin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 해당 이벤트 리스너는 이벤트 발행 상태 변경 직후에 넣기(refType, refId 사용)
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // Event 를 publish 해도 트랜잭션이 커밋 성공했을 때만 실행
    // 트랜잭션이 롤백되면 리스너가 아예 실행 안됨
    // 예약/확정 같은 확정된 사실을 기반으로 알림 저장할때 사용
    // 나중에 실시간 WebSocket 은 AFTER_COMMIT + @Async로 보내거나 저장된 알림을 기준으로 별도 비동기 처리 하기
    // DB 저장 = AFTER_COMMIT
    // 외부 발송 = " + Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // AfterCommit 리스너는 트랜잭션 밖에서 돈다 + IDENTITY는 flush 때 id 가 생긴다
    public void handle(NotificationEvent e) {
        log.info("[NOTI] handle start: {}", e);
        // 수신 유저 검증
        User recipient = userRepository.findById(e.recipientUserId())
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 수신자 입니다."));

        Notification noti = Notification.builder()
                .user(recipient)
                .type(e.type())
                .title(e.title())
                .content(e.content())
                .refType(e.refType())
                .refId(e.refId())
                .isRead(false)
                .build();

        // Insert 실제로 나가야 id가 채워지는 상태
//        notificationRepository.save(noti);

        // 해당 트랜잭션 안에서 즉시 insert(flush) 시켜서 id 즉시 생성
        Notification saved = notificationRepository.saveAndFlush(noti);
        log.info("[NOTI] saved id={}", saved.getNotificationId());

    }
}
