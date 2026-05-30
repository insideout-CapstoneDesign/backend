-- campus_ai_detection 테이블의 detect_type CHECK 제약 조건을 확장합니다.
-- AI 서버가 실내/실외 공통으로 반환하는 모든 타입을 저장할 수 있도록 합니다.

ALTER TABLE campus_ai_detection DROP CONSTRAINT IF EXISTS campus_ai_detection_detect_type_check;

ALTER TABLE campus_ai_detection
    ADD CONSTRAINT campus_ai_detection_detect_type_check
        CHECK (detect_type IN (
            -- 캠퍼스(야외) 전용
            'campus_gate',          -- 정문/게이트
            'campus_road',          -- 캠퍼스 도로
            'building_footprint',   -- 건물 영역
            'obstacle',             -- 장애물

            -- 실내 구조 (AI가 캠퍼스 도면에서도 감지)
            'wall',                 -- 벽
            'door',                 -- 문
            'corridor',             -- 복도/통행 가능 구역
            'room',                 -- 방/공간
            'elevator',             -- 엘리베이터
            'stair',                -- 계단
            'escalator',            -- 에스컬레이터
            'restroom_sign',        -- 화장실 표시

            -- 공통
            'text',                 -- 텍스트 (OCR)
            'poi_candidate',        -- POI 후보
            'node_candidate',       -- 노드 후보
            'edge_candidate'        -- 엣지 후보
        ));
