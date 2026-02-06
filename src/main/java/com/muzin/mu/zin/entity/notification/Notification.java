package com.muzin.mu.zin.entity.notification;

import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notification",
        indexes = {
                @Index(name = "idx_notification_user_created", columnList = "user_id, create_dt"),
                @Index(name = "idx_notification_user_unread", columnList = "user_id, is_read")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    // 수신자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;


    @Column(name = "title", nullable = false, length = 80)
    private String title;

    @Column(name = "content", nullable = false, length = 255)
    private String content;

//    @Column(name = "ref_reservation_id")
//    private Long refReservationId;

    // 알림에 확실한 타입이 있어야 할것같아서 변경
    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type")
    private NotificationRefType refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    // 읽음 처리 헬퍼 메서드
    public void markRead() {
        this.isRead = true;
    }
}
