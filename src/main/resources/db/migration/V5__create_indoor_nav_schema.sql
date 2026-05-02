-- =====================================================================
-- V5: Indoor Navigation Platform - Full Schema (excluding app_user)
-- 전제: V1~V4에서 app_user 테이블이 이미 생성되어 있음
-- =====================================================================

-- ============================
-- Extensions
-- ============================
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS ltree;
-- citext, pgcrypto는 V1에서 이미 활성화됨 (IF NOT EXISTS라 다시 호출해도 무방하지만 생략)


-- =====================================================================
-- A. 공용 테이블 (모든 테넌트 공유)
-- =====================================================================

-- A-1. tenant
CREATE TABLE tenant (
                        id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
                        slug          text        UNIQUE NOT NULL,
                        display_name  text        NOT NULL,
                        status        text        NOT NULL DEFAULT 'pending'
                            CHECK (status IN ('pending','approved','suspended')),
                        created_at    timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE  tenant            IS '테넌트(건물 관리자 조직)';
COMMENT ON COLUMN tenant.slug       IS 'URL/식별용 짧은 이름 (예: hyu-eng)';

-- A-3. tenant_membership (app_user는 V1에서 이미 생성)
CREATE TABLE tenant_membership (
                                   tenant_id  uuid NOT NULL REFERENCES tenant(id)   ON DELETE CASCADE,
                                   user_id    uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
                                   role       text NOT NULL CHECK (role IN ('owner','editor','viewer')),
                                   joined_at  timestamptz NOT NULL DEFAULT now(),
                                   PRIMARY KEY (tenant_id, user_id)
);

-- A-5. poi_category (ltree 트리)
CREATE TABLE poi_category (
                              id        bigserial PRIMARY KEY,
                              code      text      UNIQUE NOT NULL,
                              path      ltree     UNIQUE NOT NULL,
                              name_ko   text      NOT NULL,
                              name_en   text,
                              icon_key  text,
                              is_system boolean   NOT NULL DEFAULT true
);
CREATE INDEX poi_category_path_gist ON poi_category USING GIST (path);

-- A-6. node_kind
CREATE TABLE node_kind (
                           code             text    PRIMARY KEY,
                           name_ko          text    NOT NULL,
                           is_vertical      boolean NOT NULL DEFAULT false,
                           needs_connector  boolean NOT NULL DEFAULT false
);

-- A-7. edge_kind
CREATE TABLE edge_kind (
                           code                     text    PRIMARY KEY,
                           name_ko                  text    NOT NULL,
                           is_directed              boolean NOT NULL DEFAULT false,
                           default_cost_multiplier  numeric NOT NULL DEFAULT 1.0
);


-- =====================================================================
-- B. 건물·층·도면 (테넌트 소유)
-- =====================================================================

-- B-1. building
CREATE TABLE building (
                          id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
                          tenant_id       uuid        NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
                          name            text        NOT NULL,
                          address         text,
                          footprint       geography(Polygon, 4326),
                          entrance_count  int         NOT NULL DEFAULT 0,
                          meta            jsonb       NOT NULL DEFAULT '{}'::jsonb,
                          external_api_id text,
                          created_at      timestamptz NOT NULL DEFAULT now(),
                          UNIQUE (tenant_id, id)
);
CREATE INDEX building_tenant_idx    ON building (tenant_id);
CREATE INDEX building_footprint_gix ON building USING GIST (footprint);

-- A-4. building_directory (전역 검색용)
CREATE TABLE building_directory (
                                    id                    uuid PRIMARY KEY,    -- building.id 와 동일값 사용
                                    tenant_id             uuid NOT NULL REFERENCES tenant(id),
                                    name                  text NOT NULL,
                                    address               text,
                                    category              text,
                                    centroid              geography(Point, 4326),
                                    bbox                  geography(Polygon, 4326),
                                    is_public             boolean NOT NULL DEFAULT false,
                                    published_version_id  uuid,                -- map_version 만든 후 FK 추가
                                    updated_at            timestamptz NOT NULL DEFAULT now(),
                                    FOREIGN KEY (tenant_id, id) REFERENCES building(tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX building_dir_centroid_gix ON building_directory USING GIST (centroid);

-- B-2. floor
CREATE TABLE floor (
                       id           uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
                       tenant_id    uuid    NOT NULL,
                       building_id  uuid    NOT NULL,
                       level        int     NOT NULL,
                       name         text    NOT NULL,
                       elevation_m  numeric,
                       UNIQUE (tenant_id, building_id, level),
                       UNIQUE (tenant_id, id),
                       FOREIGN KEY (tenant_id, building_id) REFERENCES building(tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX floor_tenant_building_idx ON floor (tenant_id, building_id);

-- B-3. floorplan
CREATE TABLE floorplan (
                           id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
                           tenant_id     uuid        NOT NULL,
                           floor_id      uuid        NOT NULL,
                           image_url     text        NOT NULL,
                           image_sha256  text,
                           width_px      int         NOT NULL,
                           height_px     int         NOT NULL,
                           uploaded_by   uuid REFERENCES app_user(id),
                           is_current    boolean     NOT NULL DEFAULT false,
                           uploaded_at   timestamptz NOT NULL DEFAULT now(),
                           UNIQUE (tenant_id, id),
                           FOREIGN KEY (tenant_id, floor_id) REFERENCES floor(tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX floorplan_floor_idx ON floorplan (tenant_id, floor_id);

-- B-4. floorplan_calibration
CREATE TABLE floorplan_calibration (
                                       id             uuid              PRIMARY KEY DEFAULT gen_random_uuid(),
                                       tenant_id      uuid              NOT NULL,
                                       floorplan_id   uuid              NOT NULL,
                                       gcp            jsonb             NOT NULL,
                                       affine         double precision[],
                                       rotation_deg   numeric,
                                       rmse_m         numeric,
                                       created_at     timestamptz       NOT NULL DEFAULT now(),
                                       FOREIGN KEY (tenant_id, floorplan_id) REFERENCES floorplan(tenant_id, id) ON DELETE CASCADE
);


-- =====================================================================
-- C. 지도 버전 관리
-- =====================================================================

CREATE TABLE map_version (
                             id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
                             tenant_id          uuid        NOT NULL,
                             building_id        uuid        NOT NULL,
                             label              text        NOT NULL,
                             status             text        NOT NULL DEFAULT 'draft'
                                 CHECK (status IN ('draft','published','archived')),
                             parent_version_id  uuid        REFERENCES map_version(id),
                             created_by         uuid        REFERENCES app_user(id),
                             created_at         timestamptz NOT NULL DEFAULT now(),
                             published_at       timestamptz,
                             UNIQUE (tenant_id, id),
                             FOREIGN KEY (tenant_id, building_id) REFERENCES building(tenant_id, id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX one_published_per_building
    ON map_version (tenant_id, building_id) WHERE status = 'published';

-- building_directory.published_version_id FK는 map_version 만든 뒤에 추가
ALTER TABLE building_directory
    ADD CONSTRAINT fk_dir_published_version
        FOREIGN KEY (published_version_id) REFERENCES map_version(id) ON DELETE SET NULL;


-- =====================================================================
-- D. AI 원시 데이터
-- =====================================================================

CREATE TABLE ai_job (
                        id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
                        tenant_id      uuid        NOT NULL,
                        floorplan_id   uuid        NOT NULL,
                        status         text        NOT NULL DEFAULT 'queued'
                            CHECK (status IN ('queued','running','succeeded','failed')),
                        model_version  text,
                        params         jsonb,
                        started_at     timestamptz,
                        finished_at    timestamptz,
                        error          text,
                        FOREIGN KEY (tenant_id, floorplan_id) REFERENCES floorplan(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE ai_detection (
                              id                     uuid     PRIMARY KEY DEFAULT gen_random_uuid(),
                              tenant_id              uuid     NOT NULL,
                              job_id                 uuid     NOT NULL REFERENCES ai_job(id) ON DELETE CASCADE,
                              floorplan_id           uuid     NOT NULL,
                              detect_type            text     NOT NULL CHECK (detect_type IN
                                                                              ('wall','door','corridor','room','elevator','stair','escalator',
                                                                               'restroom_sign','text','poi_candidate','node_candidate','edge_candidate')),
                              label                  text,
                              confidence             numeric  CHECK (confidence BETWEEN 0 AND 1),
                              geom_px                geometry(Geometry, 0) NOT NULL,
                              bbox_px                box2d,
                              ocr_text               text,
                              attrs                  jsonb,
                              status                 text     NOT NULL DEFAULT 'pending'
                                  CHECK (status IN ('pending','accepted','rejected','merged')),
                              committed_entity_type  text     CHECK (committed_entity_type IN ('node','edge','poi','zone')),
                              committed_entity_id    uuid,
                              FOREIGN KEY (tenant_id, floorplan_id) REFERENCES floorplan(tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX ai_detection_geom_gix ON ai_detection USING GIST (geom_px);
CREATE INDEX ai_detection_job_idx  ON ai_detection (job_id, status);


-- =====================================================================
-- E. 그래프 엔티티
-- =====================================================================

-- E-1. node
CREATE TABLE node (
                      id              uuid              PRIMARY KEY DEFAULT gen_random_uuid(),
                      tenant_id       uuid              NOT NULL,
                      map_version_id  uuid              NOT NULL,
                      floor_id        uuid              NOT NULL,
                      kind            text              NOT NULL REFERENCES node_kind(code),
                      geom_px         geometry(Point, 0) NOT NULL,
                      geom_wgs84      geography(PointZ, 4326),
                      name_ko         text,
                      properties      jsonb             NOT NULL DEFAULT '{}'::jsonb,
                      source          text              NOT NULL DEFAULT 'manual'
                          CHECK (source IN ('ai','ai_confirmed','manual')),
                      ai_detection_id uuid              REFERENCES ai_detection(id) ON DELETE SET NULL,
                      created_at      timestamptz       NOT NULL DEFAULT now(),
                      UNIQUE (tenant_id, id),
                      FOREIGN KEY (tenant_id, map_version_id) REFERENCES map_version(tenant_id, id) ON DELETE CASCADE,
                      FOREIGN KEY (tenant_id, floor_id)       REFERENCES floor(tenant_id, id)       ON DELETE CASCADE
);
CREATE INDEX node_geom_gix    ON node USING GIST (geom_px);
CREATE INDEX node_wgs84_gix   ON node USING GIST (geom_wgs84);
CREATE INDEX node_version_idx ON node (tenant_id, map_version_id, floor_id);

-- E-2. edge
CREATE TABLE edge (
                      id              uuid                   PRIMARY KEY DEFAULT gen_random_uuid(),
                      tenant_id       uuid                   NOT NULL,
                      map_version_id  uuid                   NOT NULL,
                      from_node_id    uuid                   NOT NULL,
                      to_node_id      uuid                   NOT NULL,
                      kind            text                   NOT NULL REFERENCES edge_kind(code),
                      geom_px         geometry(LineString, 0),
                      geom_wgs84      geography(LineStringZ, 4326),
                      length_m        numeric,
                      is_directed     boolean                NOT NULL DEFAULT false,
                      base_weight     numeric                NOT NULL DEFAULT 1.0,
                      properties      jsonb                  NOT NULL DEFAULT '{}'::jsonb,
                      source          text                   NOT NULL DEFAULT 'manual'
                          CHECK (source IN ('ai','ai_confirmed','manual')),
                      ai_detection_id uuid                   REFERENCES ai_detection(id) ON DELETE SET NULL,
                      created_at      timestamptz            NOT NULL DEFAULT now(),
                      FOREIGN KEY (tenant_id, map_version_id) REFERENCES map_version(tenant_id, id) ON DELETE CASCADE,
                      FOREIGN KEY (tenant_id, from_node_id)   REFERENCES node(tenant_id, id),
                      FOREIGN KEY (tenant_id, to_node_id)     REFERENCES node(tenant_id, id)
);
CREATE INDEX edge_geom_gix      ON edge USING GIST (geom_px);
CREATE INDEX edge_version_idx   ON edge (tenant_id, map_version_id);
CREATE INDEX edge_from_node_idx ON edge (tenant_id, from_node_id);
CREATE INDEX edge_to_node_idx   ON edge (tenant_id, to_node_id);

-- E-3. poi
CREATE TABLE poi (
                     id               uuid                  PRIMARY KEY DEFAULT gen_random_uuid(),
                     tenant_id        uuid                  NOT NULL,
                     map_version_id   uuid                  NOT NULL,
                     floor_id         uuid                  NOT NULL,
                     category_id      bigint                REFERENCES poi_category(id),
                     name             text                  NOT NULL,
                     code             text,
                     geom_px          geometry(Point, 0)    NOT NULL,
                     footprint_px     geometry(Polygon, 0),
                     geom_wgs84       geography(Point, 4326),
                     anchor_node_id   uuid,
                     tags             text[],
                     attrs            jsonb                 NOT NULL DEFAULT '{}'::jsonb,
                     external_api_id  text,
                     source           text                  NOT NULL DEFAULT 'manual'
                         CHECK (source IN ('ai','ai_confirmed','manual')),
                     ai_detection_id  uuid                  REFERENCES ai_detection(id) ON DELETE SET NULL,
                     created_at       timestamptz           NOT NULL DEFAULT now(),
                     FOREIGN KEY (tenant_id, map_version_id) REFERENCES map_version(tenant_id, id) ON DELETE CASCADE,
                     FOREIGN KEY (tenant_id, floor_id)       REFERENCES floor(tenant_id, id),
                     FOREIGN KEY (tenant_id, anchor_node_id) REFERENCES node(tenant_id, id) ON DELETE SET NULL
);
CREATE INDEX poi_geom_gix    ON poi USING GIST (geom_px);
CREATE INDEX poi_version_idx ON poi (tenant_id, map_version_id);
CREATE INDEX poi_tags_gin    ON poi USING GIN (tags);

-- E-4. zone
CREATE TABLE zone (
                      id              uuid                  PRIMARY KEY DEFAULT gen_random_uuid(),
                      tenant_id       uuid                  NOT NULL,
                      map_version_id  uuid                  NOT NULL,
                      floor_id        uuid                  NOT NULL,
                      kind            text                  NOT NULL CHECK (kind IN
                                                                            ('corridor','room','restricted','public_space','outdoor_boundary')),
                      name            text,
                      geom_px         geometry(Polygon, 0)  NOT NULL,
                      properties      jsonb                 NOT NULL DEFAULT '{}'::jsonb,
                      created_at      timestamptz           NOT NULL DEFAULT now(),
                      FOREIGN KEY (tenant_id, map_version_id) REFERENCES map_version(tenant_id, id) ON DELETE CASCADE,
                      FOREIGN KEY (tenant_id, floor_id)       REFERENCES floor(tenant_id, id)       ON DELETE CASCADE
);
CREATE INDEX zone_geom_gix    ON zone USING GIST (geom_px);
CREATE INDEX zone_version_idx ON zone (tenant_id, map_version_id);


-- =====================================================================
-- F. 층간 이동 / 동적 장애물
-- =====================================================================

-- F-1. vertical_connector
CREATE TABLE vertical_connector (
                                    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
                                    tenant_id         uuid        NOT NULL,
                                    map_version_id    uuid        NOT NULL,
                                    building_id       uuid        NOT NULL,
                                    kind              text        NOT NULL CHECK (kind IN ('elevator','stair','escalator','ramp')),
                                    name              text,
                                    capacity          int,
                                    avg_wait_seconds  int,
                                    direction         text        CHECK (direction IN ('up','down','both')),
                                    accessibility     jsonb       NOT NULL DEFAULT '{}'::jsonb,
                                    UNIQUE (tenant_id, id),
                                    FOREIGN KEY (tenant_id, map_version_id) REFERENCES map_version(tenant_id, id) ON DELETE CASCADE,
                                    FOREIGN KEY (tenant_id, building_id)    REFERENCES building(tenant_id, id)    ON DELETE CASCADE
);

-- F-2. vertical_connector_node
CREATE TABLE vertical_connector_node (
                                         tenant_id     uuid NOT NULL,
                                         connector_id  uuid NOT NULL,
                                         node_id       uuid NOT NULL,
                                         floor_id      uuid NOT NULL,
                                         PRIMARY KEY (connector_id, node_id),
                                         FOREIGN KEY (tenant_id, connector_id) REFERENCES vertical_connector(tenant_id, id) ON DELETE CASCADE,
                                         FOREIGN KEY (tenant_id, node_id)      REFERENCES node(tenant_id, id)               ON DELETE CASCADE,
                                         FOREIGN KEY (tenant_id, floor_id)     REFERENCES floor(tenant_id, id)              ON DELETE CASCADE
);

-- F-3. obstacle
CREATE TABLE obstacle (
                          id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
                          tenant_id          uuid        NOT NULL,
                          building_id        uuid        NOT NULL,
                          floor_id           uuid,
                          kind               text        NOT NULL CHECK (kind IN ('construction','closed','slippery','event')),
                          geom_px            geometry,
                          affected_edge_ids  uuid[],
                          extra_cost         numeric     NOT NULL DEFAULT 0,
                          is_blocking        boolean     NOT NULL DEFAULT false,
                          active_from        timestamptz,
                          active_to          timestamptz,
                          note               text,
                          created_at         timestamptz NOT NULL DEFAULT now(),
                          FOREIGN KEY (tenant_id, building_id) REFERENCES building(tenant_id, id) ON DELETE CASCADE,
                          FOREIGN KEY (tenant_id, floor_id)    REFERENCES floor(tenant_id, id)    ON DELETE CASCADE
);
CREATE INDEX obstacle_active_idx ON obstacle (active_from, active_to) WHERE is_blocking = true;


-- =====================================================================
-- 시드 데이터 (마스터 테이블)
-- =====================================================================

INSERT INTO node_kind (code, name_ko, is_vertical, needs_connector) VALUES
                                                                        ('corridor',   '복도 교차점',     false, false),
                                                                        ('door',       '문',             false, false),
                                                                        ('entrance',   '건물 출입구',     false, false),
                                                                        ('stair',      '계단',           true,  true),
                                                                        ('elevator',   '엘리베이터',      true,  true),
                                                                        ('escalator',  '에스컬레이터',    true,  true),
                                                                        ('poi_anchor', 'POI 앵커',       false, false),
                                                                        ('portal',     '포탈(가상노드)',  true,  false);

INSERT INTO edge_kind (code, name_ko, is_directed, default_cost_multiplier) VALUES
                                                                                ('walkway',   '일반 보행',      false, 1.0),
                                                                                ('stair',     '계단 이동',      false, 3.0),
                                                                                ('elevator',  '엘리베이터 이동', false, 5.0),
                                                                                ('escalator', '에스컬레이터',   true,  2.0),
                                                                                ('door',      '문 통과',        false, 1.2),
                                                                                ('outdoor',   '실외 보행',      false, 1.0);

INSERT INTO poi_category (code, path, name_ko) VALUES
                                                   ('facility',           'facility',           '시설'),
                                                   ('facility.restroom',  'facility.restroom',  '화장실'),
                                                   ('facility.elevator',  'facility.elevator',  '엘리베이터'),
                                                   ('facility.stair',     'facility.stair',     '계단'),
                                                   ('facility.parking',   'facility.parking',   '주차장'),
                                                   ('store',              'store',              '상점'),
                                                   ('store.cafe',         'store.cafe',         '카페'),
                                                   ('store.food',         'store.food',         '음식점'),
                                                   ('store.retail',       'store.retail',       '판매점'),
                                                   ('academic',           'academic',           '학술'),
                                                   ('academic.classroom', 'academic.classroom', '강의실'),
                                                   ('academic.lab',       'academic.lab',       '실험실'),
                                                   ('academic.office',    'academic.office',    '사무실');