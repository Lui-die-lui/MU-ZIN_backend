-- 채팅 메시지 수정·삭제 (2026-10-18)
-- 수정: 보낸 사람만, 보낸 지 24시간 안 / 삭제: 보낸 사람만, 언제든 (내용을 지우고 "삭제된 메시지"로 표시)
ALTER TABLE chat_message ADD COLUMN edited_dt timestamp(6);
ALTER TABLE chat_message ADD COLUMN deleted_dt timestamp(6);
