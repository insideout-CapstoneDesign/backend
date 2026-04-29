-- display_name 무결성 강화:
-- 1) 길이 10자 초과 데이터 정리
-- 2) 유니크 재정렬(충돌 없는 값으로 보정)
-- 3) 길이 체크 제약 추가

-- 10자 초과 닉네임은 우선 10자로 자른다.
UPDATE app_user
SET display_name = LEFT(display_name, 10)
WHERE CHAR_LENGTH(display_name) > 10;

-- 중복 닉네임은 id 일부를 붙여 충돌 없이 정리한다.
WITH ranked AS (
    SELECT id,
           display_name,
           ROW_NUMBER() OVER (PARTITION BY display_name ORDER BY created_at, id) AS rn
    FROM app_user
)
UPDATE app_user u
SET display_name = CONCAT(
        LEFT(u.display_name, 1),
        '_',
        SUBSTRING(REPLACE(u.id::text, '-', '') FROM 1 FOR 8)
)
FROM ranked
WHERE u.id = ranked.id
  AND ranked.rn > 1;

ALTER TABLE app_user
    DROP CONSTRAINT IF EXISTS uq_app_user_display_name;

ALTER TABLE app_user
    ADD CONSTRAINT uq_app_user_display_name UNIQUE (display_name);

ALTER TABLE app_user
    DROP CONSTRAINT IF EXISTS chk_app_user_display_name_len;

ALTER TABLE app_user
    ADD CONSTRAINT chk_app_user_display_name_len
        CHECK (CHAR_LENGTH(display_name) <= 10);
