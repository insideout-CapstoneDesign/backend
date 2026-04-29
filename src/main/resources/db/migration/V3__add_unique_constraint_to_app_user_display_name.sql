-- display_name 중복 방지 제약 추가
-- 기존 중복 데이터는 _2, _3 접미사를 붙여 유니크하게 정리
WITH ranked AS (
    SELECT id,
           display_name,
           ROW_NUMBER() OVER (PARTITION BY display_name ORDER BY created_at, id) AS rn
    FROM app_user
)
UPDATE app_user u
SET display_name = CONCAT(u.display_name, '_', ranked.rn)
FROM ranked
WHERE u.id = ranked.id
  AND ranked.rn > 1;

ALTER TABLE app_user
    DROP CONSTRAINT IF EXISTS uq_app_user_display_name;

ALTER TABLE app_user
    ADD CONSTRAINT uq_app_user_display_name UNIQUE (display_name);
