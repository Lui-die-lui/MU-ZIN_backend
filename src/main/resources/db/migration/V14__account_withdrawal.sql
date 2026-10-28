-- 회원 탈퇴: 공유 기록의 FK는 유지하되 계정은 익명화하고 즉시 로그인 불가 처리
ALTER TABLE users ADD COLUMN withdrawn_dt timestamp(6);
ALTER TABLE users ADD COLUMN ever_suspended boolean NOT NULL DEFAULT false;
UPDATE users SET ever_suspended = true WHERE suspended_dt IS NOT NULL;
CREATE INDEX idx_users_withdrawn ON users (withdrawn_dt) WHERE withdrawn_dt IS NOT NULL;

-- 정지 이력이 있는 탈퇴 회원만 이메일 HMAC과 차단 만료 시각을 분리 보관
CREATE TABLE rejoin_block (
    email_hmac    varchar(64) PRIMARY KEY,
    blocked_until timestamp(6) NOT NULL,
    create_dt     timestamp(6) NOT NULL
);
CREATE INDEX idx_rejoin_block_until ON rejoin_block (blocked_until);
