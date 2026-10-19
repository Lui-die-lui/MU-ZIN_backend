-- 아티스트 심사 결과 알림 (2026-10-19)
-- 관리자가 아티스트 신청을 승인/반려하면 신청자에게 알림 (ref 없음 -> 프론트는 type 으로 아티스트 화면 이동)
ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN ('RESERVATION_REQUESTED', 'RESERVATION_CONFIRMED', 'RESERVATION_REJECTED',
                    'RESERVATION_COMPLETION_PENDING', 'RESERVATION_COMPLETED',
                    'RESERVATION_CANCELED_BY_USER', 'RESERVATION_CANCELED_BY_ARTIST',
                    'RESERVATION_EXPIRED', 'REVIEW_CREATED', 'REVIEW_REPLIED',
                    'INQUIRY_CREATED', 'INQUIRY_REPLIED', 'INQUIRY_TO_CHAT',
                    'ARTIST_APPROVED', 'ARTIST_REJECTED'));
