-- enum 문자열(대문자)과 DB 체크 제약 값을 일치시킴
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS chk_app_user_global_role;

UPDATE app_user
SET global_role = UPPER(global_role)
WHERE global_role IN ('sys_admin', 'tenant_user', 'end_user');

ALTER TABLE app_user
    ALTER COLUMN global_role SET DEFAULT 'END_USER';

ALTER TABLE app_user
    ADD CONSTRAINT chk_app_user_global_role
        CHECK (global_role IN ('SYS_ADMIN', 'TENANT_USER', 'END_USER'));
