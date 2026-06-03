WITH duplicate_drafts AS (
    SELECT id
    FROM (
        SELECT
            id,
            ROW_NUMBER() OVER (
                PARTITION BY tenant_id, building_id, map_type
                ORDER BY created_at DESC, id DESC
            ) AS row_num
        FROM map_version
        WHERE status = 'draft'
          AND map_type = 'BUILDING'
          AND building_id IS NOT NULL
    ) ranked_drafts
    WHERE row_num > 1
)
DELETE FROM map_version
WHERE id IN (SELECT id FROM duplicate_drafts);

CREATE UNIQUE INDEX one_draft_building_map_version
    ON map_version (tenant_id, building_id, map_type)
    WHERE status = 'draft' AND map_type = 'BUILDING';
