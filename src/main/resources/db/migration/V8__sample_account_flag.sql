-- 샘플(시연용) 계정 구분 (2026-10-18)
-- 지금까지 샘플 계정을 구분하는 컬럼이 없어서 이름의 "(샘플)" 이나 이메일로만 알 수 있었음
-- -> 계정에 표시 컬럼을 두고, 추천 화면은 이 값으로 운영에서 제외 / 개발에서 "샘플" 표시 (app.sample-data.show)
ALTER TABLE users ADD COLUMN is_sample boolean NOT NULL DEFAULT false;

-- 기존 샘플: 로컬에서 손으로 넣은 시연 계정(demo.*@muzin.test)만. 운영 DB 에는 해당 계정이 없어 바뀌는 행 없음
UPDATE users SET is_sample = true WHERE email LIKE 'demo.%@muzin.test';
