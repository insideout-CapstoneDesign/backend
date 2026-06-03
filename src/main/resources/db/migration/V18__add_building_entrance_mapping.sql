INSERT INTO poi_category (code, path, name_ko, is_system)
VALUES ('facility.entrance', 'facility.entrance', '출입구', true)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE building
    ADD CONSTRAINT uq_building_tenant_id_id_campus_id
        UNIQUE (tenant_id, id, campus_id);

CREATE TABLE building_entrance_mapping (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    campus_id uuid NOT NULL,
    building_id uuid NOT NULL,
    campus_gate_id text NOT NULL,
    entrance_node_id uuid NOT NULL REFERENCES node(id) ON DELETE CASCADE,
    entrance_poi_id uuid REFERENCES poi(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, campus_id, campus_gate_id),
    UNIQUE (tenant_id, entrance_node_id),
    FOREIGN KEY (tenant_id, building_id, campus_id)
        REFERENCES building(tenant_id, id, campus_id) ON DELETE CASCADE
);

CREATE INDEX building_entrance_mapping_building_idx
    ON building_entrance_mapping (tenant_id, building_id, created_at);
