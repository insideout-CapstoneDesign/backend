-- V6: Edge → Node FK에 ON DELETE CASCADE 추가
--     POI → Floor FK에 ON DELETE CASCADE 추가 (map_version_id와 동작 일관성 맞춤)

-- 1. edge 테이블: from_node_id, to_node_id FK 재설정
ALTER TABLE edge
    DROP CONSTRAINT IF EXISTS edge_tenant_id_from_node_id_fkey,
    DROP CONSTRAINT IF EXISTS edge_tenant_id_to_node_id_fkey;

ALTER TABLE edge
    ADD CONSTRAINT edge_tenant_id_from_node_id_fkey
        FOREIGN KEY (tenant_id, from_node_id) REFERENCES node(tenant_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT edge_tenant_id_to_node_id_fkey
        FOREIGN KEY (tenant_id, to_node_id)   REFERENCES node(tenant_id, id) ON DELETE CASCADE;

-- 2. poi 테이블: floor_id FK 재설정
ALTER TABLE poi
    DROP CONSTRAINT IF EXISTS poi_tenant_id_floor_id_fkey;

ALTER TABLE poi
    ADD CONSTRAINT poi_tenant_id_floor_id_fkey
        FOREIGN KEY (tenant_id, floor_id) REFERENCES floor(tenant_id, id) ON DELETE CASCADE;
