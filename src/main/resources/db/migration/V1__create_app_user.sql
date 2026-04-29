-- 이메일 대소문자 무시 + UUID 생성 확장
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS app_user (
                                        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email citext NOT NULL UNIQUE,
    password_hash text NOT NULL,
    display_name text NOT NULL,
    global_role text NOT NULL DEFAULT 'end_user',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_app_user_global_role
    CHECK (global_role IN ('sys_admin', 'tenant_user', 'end_user'))
    );

CREATE INDEX IF NOT EXISTS idx_app_user_created_at ON app_user(created_at);
