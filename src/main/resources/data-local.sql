-- ============================================================
-- Local Development / Testing Seed Data
-- ============================================================

BEGIN;

-- 1. 기존 테스트 데이터가 존재할 경우 충돌 방지를 위해 선제 삭제
DELETE FROM building WHERE id = '44444444-4444-4444-8444-444444444444';
DELETE FROM campus WHERE id = '33333333-3333-4333-8333-333333333333';
DELETE FROM tenant_membership WHERE tenant_id = '22222222-2222-4222-8222-222222222222';
DELETE FROM tenant WHERE id = '22222222-2222-4222-8222-222222222222';
DELETE FROM app_user WHERE id = '11111111-1111-4111-8111-111111111111';

-- 2. 사용자(User) 등록 (email: test@t.t, displayName: tt, password: test1234!)
INSERT INTO app_user (
    id,
    email,
    password_hash,
    display_name,
    global_role,
    created_at,
    updated_at
) VALUES (
    '11111111-1111-4111-8111-111111111111',
    'test@t.t',
    '$2a$10$H6pHdmIclrIeR0i1jfrueegr1/iZ/Q7VOQKxZcpwEoA2tRjEgRxVW',
    'tt',
    'TENANT_USER',
    now(),
    now()
);

-- 3. 테넌트(Tenant) 등록
INSERT INTO tenant (
    id,
    slug,
    display_name,
    status,
    created_at
) VALUES (
    '22222222-2222-4222-8222-222222222222',
    'test-tenant-slug',
    '테스트 테넌트',
    'approved',
    now()
);

-- 4. 테넌트 멤버십 매핑 (유저를 테넌트 소유자로 등록)
INSERT INTO tenant_membership (
    tenant_id,
    user_id,
    role,
    joined_at
) VALUES (
    '22222222-2222-4222-8222-222222222222',
    '11111111-1111-4111-8111-111111111111',
    'owner',
    now()
);

-- 5. 테스트 캠퍼스(Campus) 등록
INSERT INTO campus (
    id,
    tenant_id,
    name,
    address,
    boundary,
    centroid,
    primary_entrance,
    primary_entrance_name,
    meta,
    created_at
) VALUES (
    '33333333-3333-4333-8333-333333333333',
    '22222222-2222-4222-8222-222222222222',
    '테스트 캠퍼스',
    '서울특별시 성동구 왕십리로 222',
    ST_GeogFromText('SRID=4326;POLYGON((127.00005 37.55550,127.00135 37.55550,127.00135 37.55605,127.00005 37.55605,127.00005 37.55550))'),
    ST_GeogFromText('SRID=4326;POINT(127.00072 37.55578)'),
    ST_GeogFromText('SRID=4326;POINT(127.00020 37.55570)'),
    '정문 게이트',
    '{"theme":"test-theme"}'::jsonb,
    now()
);

-- 6. 테스트 빌딩(Building) 등록
INSERT INTO building (
    id,
    tenant_id,
    campus_id,
    name,
    address,
    footprint,
    entrance_count,
    meta,
    created_at
) VALUES (
    '44444444-4444-4444-8444-444444444444',
    '22222222-2222-4222-8222-222222222222',
    '33333333-3333-4333-8333-333333333333',
    '테스트 빌딩',
    '서울특별시 성동구 왕십리로 222 테스트 빌딩',
    ST_GeogFromText('SRID=4326;POLYGON((127.00084 37.55574,127.00112 37.55574,127.00112 37.55594,127.00084 37.55594,127.00084 37.55574))'),
    1,
    '{"buildingType":"academic"}'::jsonb,
    now()
);

COMMIT;
