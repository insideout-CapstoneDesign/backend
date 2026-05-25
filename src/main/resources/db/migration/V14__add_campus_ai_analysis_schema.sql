CREATE TABLE campus_ai_job (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL,
    campus_map_id  uuid        NOT NULL,
    status         text        NOT NULL DEFAULT 'queued'
        CHECK (status IN ('queued','running','succeeded','failed')),
    model_version  text,
    params         jsonb,
    started_at     timestamptz,
    finished_at    timestamptz,
    error          text,
    FOREIGN KEY (tenant_id, campus_map_id) REFERENCES campus_map(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE campus_ai_detection (
    id                     uuid     PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              uuid     NOT NULL,
    job_id                 uuid     NOT NULL REFERENCES campus_ai_job(id) ON DELETE CASCADE,
    campus_map_id          uuid     NOT NULL,
    detect_type            text     NOT NULL CHECK (detect_type IN
                                                    ('campus_gate','campus_road','building_footprint','obstacle','text','poi_candidate','node_candidate','edge_candidate')),
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
    FOREIGN KEY (tenant_id, campus_map_id) REFERENCES campus_map(tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX campus_ai_detection_geom_gix ON campus_ai_detection USING GIST (geom_px);
CREATE INDEX campus_ai_detection_job_idx  ON campus_ai_detection (job_id, status);
