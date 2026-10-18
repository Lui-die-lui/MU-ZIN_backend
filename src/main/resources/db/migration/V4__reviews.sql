-- 리뷰 기능 완성 (2026-10-18)

-- 1) 리뷰 알림: 리뷰 작성 -> 아티스트, 답글 작성 -> 리뷰 작성자
ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN ('RESERVATION_REQUESTED', 'RESERVATION_CONFIRMED', 'RESERVATION_REJECTED',
                    'RESERVATION_COMPLETION_PENDING', 'RESERVATION_COMPLETED',
                    'RESERVATION_CANCELED_BY_USER', 'RESERVATION_CANCELED_BY_ARTIST',
                    'RESERVATION_EXPIRED', 'REVIEW_CREATED', 'REVIEW_REPLIED'));

ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_ref_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_ref_type_check
    CHECK (ref_type IN ('RESERVATION', 'CHAT_ROOM', 'REVIEW'));

-- 2) 리뷰/답글 길이 255 -> 500자
ALTER TABLE review ALTER COLUMN content TYPE varchar(500);
ALTER TABLE review_reply ALTER COLUMN content TYPE varchar(500);

-- 3) 별점 1~5 (DB에서도 보장)
ALTER TABLE review DROP CONSTRAINT IF EXISTS review_rating_check;
ALTER TABLE review ADD CONSTRAINT review_rating_check CHECK (rating BETWEEN 1 AND 5);
