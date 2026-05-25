-- 1. campus_ai_job 테이블에 복합 UNIQUE 제약 조건 추가 (외래 키가 참조할 수 있도록 설정)
ALTER TABLE campus_ai_job
    ADD CONSTRAINT uq_campus_ai_job_composite UNIQUE (id, tenant_id, campus_map_id);

-- 2. campus_ai_detection 테이블의 기존 단일 컬럼 외래 키 제약 조건 제거
-- PostgreSQL이 자동으로 생성하는 단일 외래 키 제약 조건 이름은 "campus_ai_detection_job_id_fkey" 입니다.
ALTER TABLE campus_ai_detection
    DROP CONSTRAINT IF EXISTS campus_ai_detection_job_id_fkey;

-- 3. campus_ai_detection 테이블에 복합 외래 키 제약 조건 추가
-- (job_id, tenant_id, campus_map_id)가 campus_ai_job의 (id, tenant_id, campus_map_id)를 참조하게 하여 테넌트 불일치를 원천 차단
ALTER TABLE campus_ai_detection
    ADD CONSTRAINT fk_campus_ai_detection_job_composite
    FOREIGN KEY (job_id, tenant_id, campus_map_id)
    REFERENCES campus_ai_job(id, tenant_id, campus_map_id)
    ON DELETE CASCADE;
