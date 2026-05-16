-- Add POI icon detection types and editable floorplan objects.

ALTER TABLE ai_detection
    DROP CONSTRAINT IF EXISTS ai_detection_detect_type_check;

ALTER TABLE ai_detection
    ADD CONSTRAINT ai_detection_detect_type_check
        CHECK (detect_type IN (
            -- Indoor structure
            'wall',
            'door',
            'corridor',
            'room',
            'elevator',
            'stair',
            'escalator',
            'restroom_sign',

            -- Outdoor
            'building',
            'gate',
            'road',
            'sidewalk',
            'crosswalk',
            'parking',
            'landmark',

            -- POI icons
            'accessible_restroom',
            'aed',
            'atm',
            'cafe',
            'clothing_alteration',
            'family_restroom',
            'infodesk',
            'phone_charging',
            'restroom_female',
            'restroom_male',
            'shoe_repair',
            'storage_locker',
            'subway_station',
            'water_fountain',

            -- Common
            'text',
            'poi_candidate',
            'node_candidate',
            'edge_candidate'
        ));

ALTER TABLE ai_detection
    DROP CONSTRAINT IF EXISTS ai_detection_committed_entity_type_check;

ALTER TABLE ai_detection
    ADD CONSTRAINT ai_detection_committed_entity_type_check
        CHECK (committed_entity_type IN (
            'node',
            'edge',
            'poi',
            'zone',
            'floorplan_object'
        ));

INSERT INTO poi_category (code, path, name_ko)
VALUES
    ('facility.accessible_restroom', 'facility.accessible_restroom', '장애인 화장실'),
    ('facility.aed',                 'facility.aed',                 'AED'),
    ('facility.escalator',           'facility.escalator',           '에스컬레이터'),
    ('facility.family_restroom',     'facility.family_restroom',     '가족 화장실'),
    ('facility.infodesk',            'facility.infodesk',            '안내데스크'),
    ('facility.phone_charging',      'facility.phone_charging',      '휴대폰 충전'),
    ('facility.restroom_female',     'facility.restroom_female',     '여자 화장실'),
    ('facility.restroom_male',       'facility.restroom_male',       '남자 화장실'),
    ('facility.storage_locker',      'facility.storage_locker',      '물품보관함'),
    ('facility.subway_station',      'facility.subway_station',      '지하철역'),
    ('facility.water_fountain',      'facility.water_fountain',      '정수기'),
    ('facility.atm',                 'facility.atm',                 'ATM'),
    ('store.clothing_alteration',    'store.clothing_alteration',    '수선실'),
    ('store.shoe_repair',            'store.shoe_repair',            '구두수선')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE floorplan_object (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    map_version_id uuid NOT NULL,
    floor_id uuid NOT NULL,
    kind text NOT NULL CHECK (kind IN (
        'wall',
        'door',
        'room_boundary',
        'corridor_boundary'
    )),
    geom_px geometry NOT NULL,
    properties jsonb NOT NULL DEFAULT '{}'::jsonb,
    source text NOT NULL DEFAULT 'manual'
        CHECK (source IN ('ai', 'ai_confirmed', 'manual')),
    ai_detection_id uuid REFERENCES ai_detection(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, map_version_id) REFERENCES map_version(tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, floor_id) REFERENCES floor(tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX floorplan_object_geom_gix
    ON floorplan_object USING GIST (geom_px);

CREATE INDEX floorplan_object_version_idx
    ON floorplan_object (tenant_id, map_version_id, floor_id);

CREATE INDEX floorplan_object_kind_idx
    ON floorplan_object (kind);
