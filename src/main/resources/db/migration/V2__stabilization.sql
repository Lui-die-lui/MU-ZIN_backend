-- 안정화 작업 (2026-10-17)
-- 1) 방치된 예약 요청 만료 상태 추가
-- 2) 동시 수정 충돌 감지용 낙관적 락 버전 컬럼
-- 3) 슬롯당 활성 예약 1건을 DB 레벨에서 보장
-- 4) 계정 정지(신원 회수)

-- 1) 예약 상태 EXPIRED + 만료 시각
ALTER TABLE lesson_reservation DROP CONSTRAINT IF EXISTS lesson_reservation_status_check;
ALTER TABLE lesson_reservation ADD CONSTRAINT lesson_reservation_status_check
    CHECK (status IN ('REQUESTED', 'CONFIRMED', 'COMPLETION_PENDING', 'REJECTED', 'CANCELED', 'COMPLETED', 'EXPIRED'));
ALTER TABLE lesson_reservation ADD COLUMN expired_dt timestamp(6) without time zone;

ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN ('RESERVATION_REQUESTED', 'RESERVATION_CONFIRMED', 'RESERVATION_REJECTED',
                    'RESERVATION_COMPLETION_PENDING', 'RESERVATION_COMPLETED',
                    'RESERVATION_CANCELED_BY_USER', 'RESERVATION_CANCELED_BY_ARTIST',
                    'RESERVATION_EXPIRED'));

-- 2) 낙관적 락 (기존 행은 0부터 시작)
ALTER TABLE lesson_reservation ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE lesson_time_slot ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- 3) 한 슬롯에 진행 중인 예약은 1건만 (취소/거절/만료/완료 이력은 여러 건 허용)
CREATE UNIQUE INDEX uk_lesson_reservation_active_slot
    ON lesson_reservation (slot_id)
    WHERE status IN ('REQUESTED', 'CONFIRMED', 'COMPLETION_PENDING');

-- 4) 계정 정지: 정지된 계정은 유효한 토큰이 있어도 모든 인증 API에서 403
ALTER TABLE users ADD COLUMN suspended_dt timestamp(6) without time zone;
ALTER TABLE users ADD COLUMN suspended_reason varchar(255);
