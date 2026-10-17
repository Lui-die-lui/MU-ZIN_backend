-- 완료된 예약의 슬롯은 더 이상 예약 중이 아니므로 CLOSED 상태여야 한다.
-- 기존 데이터 중 예약만 COMPLETED로 바뀌고 슬롯이 BOOKED로 남은 행을 보정한다.
UPDATE lesson_time_slot slot
SET status = 'CLOSED',
    update_dt = CURRENT_TIMESTAMP,
    version = slot.version + 1
FROM lesson_reservation reservation
WHERE reservation.slot_id = slot.time_slot_id
  AND reservation.status = 'COMPLETED'
  AND slot.status = 'BOOKED';
