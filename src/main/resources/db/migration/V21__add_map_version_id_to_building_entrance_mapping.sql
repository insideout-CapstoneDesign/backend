-- Add map_version_id to building_entrance_mapping for version isolation

-- 1. Add map_version_id column as nullable initially (using IF NOT EXISTS to prevent error if already run)
ALTER TABLE building_entrance_mapping ADD COLUMN IF NOT EXISTS map_version_id uuid;

-- 2. Populate map_version_id for existing records based on the most relevant map_version of the building
UPDATE building_entrance_mapping m
SET map_version_id = (
    SELECT mv.id
    FROM map_version mv
    WHERE mv.building_id = m.building_id
    ORDER BY 
        CASE WHEN mv.status = 'published' THEN 1 
             WHEN mv.status = 'draft' THEN 2 
             ELSE 3 
        END, 
        mv.created_at DESC
    LIMIT 1
)
WHERE m.map_version_id IS NULL;

-- 3. Delete any orphaned entrance mappings that could not be mapped to any map version
DELETE FROM building_entrance_mapping WHERE map_version_id IS NULL;

-- 4. Alter column to NOT NULL and add foreign key constraint (drop first to prevent duplicate constraint error)
ALTER TABLE building_entrance_mapping ALTER COLUMN map_version_id SET NOT NULL;
ALTER TABLE building_entrance_mapping DROP CONSTRAINT IF EXISTS fk_building_entrance_mapping_map_version;
ALTER TABLE building_entrance_mapping 
    ADD CONSTRAINT fk_building_entrance_mapping_map_version 
    FOREIGN KEY (map_version_id) REFERENCES map_version(id) ON DELETE CASCADE;

-- 5. Drop old unique constraints (including anonymous ones from V18, named ones from V20, and named ones from previous V21 run)
DO $$
DECLARE
    r RECORD;
BEGIN
    -- Drop constraints matching tenant_id, campus_id, campus_gate_id
    FOR r IN (
        SELECT conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE rel.relname = 'building_entrance_mapping'
          AND con.contype = 'u'
          AND pg_get_constraintdef(con.oid) LIKE '%UNIQUE (tenant_id, campus_id, campus_gate_id)%'
    ) LOOP
        EXECUTE 'ALTER TABLE building_entrance_mapping DROP CONSTRAINT IF EXISTS ' || quote_ident(r.conname);
    END LOOP;

    -- Drop constraints matching tenant_id, entrance_node_id
    FOR r IN (
        SELECT conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE rel.relname = 'building_entrance_mapping'
          AND con.contype = 'u'
          AND pg_get_constraintdef(con.oid) LIKE '%UNIQUE (tenant_id, entrance_node_id)%'
    ) LOOP
        EXECUTE 'ALTER TABLE building_entrance_mapping DROP CONSTRAINT IF EXISTS ' || quote_ident(r.conname);
    END LOOP;
END $$;

-- 6. Add new unique constraints that include map_version_id
ALTER TABLE building_entrance_mapping DROP CONSTRAINT IF EXISTS uk_building_entrance_mapping_gate;
ALTER TABLE building_entrance_mapping DROP CONSTRAINT IF EXISTS uk_building_entrance_mapping_node;

ALTER TABLE building_entrance_mapping 
    ADD CONSTRAINT uk_building_entrance_mapping_gate 
    UNIQUE (tenant_id, campus_id, map_version_id, campus_gate_id);

ALTER TABLE building_entrance_mapping 
    ADD CONSTRAINT uk_building_entrance_mapping_node 
    UNIQUE (tenant_id, building_id, map_version_id, entrance_node_id);
