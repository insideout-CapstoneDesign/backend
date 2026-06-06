-- Remove duplicates for uk_building_entrance_mapping_gate (keep latest by created_at)
DELETE FROM building_entrance_mapping
WHERE id NOT IN (
    SELECT id
    FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY tenant_id, campus_id, campus_gate_id ORDER BY created_at DESC) as rn
        FROM building_entrance_mapping
    ) t
    WHERE t.rn = 1
);

-- Remove duplicates for uk_building_entrance_mapping_node (keep latest by created_at)
DELETE FROM building_entrance_mapping
WHERE id NOT IN (
    SELECT id
    FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY tenant_id, building_id, entrance_node_id ORDER BY created_at DESC) as rn
        FROM building_entrance_mapping
    ) t
    WHERE t.rn = 1
);

-- Add unique constraints
ALTER TABLE building_entrance_mapping
ADD CONSTRAINT uk_building_entrance_mapping_gate UNIQUE (tenant_id, campus_id, campus_gate_id);

ALTER TABLE building_entrance_mapping
ADD CONSTRAINT uk_building_entrance_mapping_node UNIQUE (tenant_id, building_id, entrance_node_id);
