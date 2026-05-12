-- V6__expand_detect_types_for_outdoor.sql

-- 기존 CHECK 제약 삭제
ALTER TABLE ai_detection DROP CONSTRAINT ai_detection_detect_type_check;

-- 외부(캠퍼스) 도면용 타입 추가하여 새 CHECK 생성
ALTER TABLE ai_detection
    ADD CONSTRAINT ai_detection_detect_type_check
        CHECK (detect_type IN (
            -- 실내용 (기존)
                               'wall', 'door', 'corridor', 'room',
                               'elevator', 'stair', 'escalator', 'restroom_sign',

            -- 외부(단지)용 ⭐ 추가
                               'building',         -- 건물 외곽 폴리곤
                               'gate',             -- 출입문/대문
                               'road',             -- 차도
                               'sidewalk',         -- 보도
                               'crosswalk',        -- 횡단보도
                               'parking',          -- 주차장
                               'landmark',         -- 랜드마크 (분수, 동상 등)

            -- 공통
                               'text',
                               'poi_candidate',
                               'node_candidate',
                               'edge_candidate'
            ));