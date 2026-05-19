-- Hybrid navigation hierarchy:
-- Campus -> Building -> Room/POI.

CREATE TABLE campus (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name text NOT NULL,
    address text,
    boundary geography(Polygon, 4326),
    centroid geography(Point, 4326),
    primary_entrance geography(Point, 4326) NOT NULL,
    primary_entrance_name text,
    meta jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id)
);

CREATE INDEX campus_tenant_idx ON campus (tenant_id);
CREATE INDEX campus_boundary_gix ON campus USING GIST (boundary);
CREATE INDEX campus_primary_entrance_gix ON campus USING GIST (primary_entrance);

CREATE TABLE campus_map (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    campus_id uuid NOT NULL,
    bucket_name text NOT NULL,
    object_key text NOT NULL,
    image_url text,
    width_px int NOT NULL,
    height_px int NOT NULL,
    uploaded_by uuid REFERENCES app_user(id),
    is_current boolean NOT NULL DEFAULT false,
    uploaded_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, campus_id) REFERENCES campus(tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX campus_map_campus_idx ON campus_map (tenant_id, campus_id);

ALTER TABLE building
    ADD COLUMN campus_id uuid,
    ADD CONSTRAINT fk_building_campus
        FOREIGN KEY (tenant_id, campus_id) REFERENCES campus(tenant_id, id);

CREATE INDEX building_campus_idx ON building (tenant_id, campus_id);

ALTER TABLE building_directory
    ADD COLUMN campus_id uuid,
    ADD CONSTRAINT fk_building_directory_campus
        FOREIGN KEY (tenant_id, campus_id) REFERENCES campus(tenant_id, id);

ALTER TABLE map_version
    ADD COLUMN map_type text NOT NULL DEFAULT 'BUILDING',
    ADD COLUMN campus_id uuid,
    ALTER COLUMN building_id DROP NOT NULL,
    ADD CONSTRAINT chk_map_version_scope
        CHECK (
            (map_type = 'BUILDING' AND building_id IS NOT NULL AND campus_id IS NULL)
            OR
            (map_type = 'CAMPUS' AND campus_id IS NOT NULL AND building_id IS NULL)
        ),
    ADD CONSTRAINT fk_map_version_campus
        FOREIGN KEY (tenant_id, campus_id) REFERENCES campus(tenant_id, id) ON DELETE CASCADE;

CREATE INDEX map_version_campus_idx ON map_version (tenant_id, campus_id);

ALTER TABLE node
    ALTER COLUMN floor_id DROP NOT NULL;

INSERT INTO node_kind (code, name_ko, is_vertical, needs_connector)
VALUES
    ('campus_gate', '캠퍼스 출입구', false, false),
    ('campus_road', '캠퍼스 보행로', false, false),
    ('building_anchor', '건물 연결 앵커', false, false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO edge_kind (code, name_ko, is_directed, default_cost_multiplier)
VALUES
    ('campus_walkway', '캠퍼스 내부 보행', false, 1.0)
ON CONFLICT (code) DO NOTHING;
