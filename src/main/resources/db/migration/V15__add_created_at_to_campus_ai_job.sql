-- campus_ai_job 테이블에 최신 Job 정렬을 보장하기 위한 created_at 시계열 컬럼 추가
ALTER TABLE campus_ai_job ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
