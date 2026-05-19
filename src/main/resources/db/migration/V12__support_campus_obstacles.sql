-- Allow obstacles to apply to either a building graph or a campus graph.

ALTER TABLE obstacle
    ADD COLUMN campus_id uuid;

ALTER TABLE obstacle
    ALTER COLUMN building_id DROP NOT NULL;

ALTER TABLE obstacle
    ADD CONSTRAINT fk_obstacle_campus
        FOREIGN KEY (tenant_id, campus_id) REFERENCES campus(tenant_id, id) ON DELETE CASCADE;

ALTER TABLE obstacle
    ADD CONSTRAINT chk_obstacle_scope
        CHECK (
            (building_id IS NOT NULL AND campus_id IS NULL)
            OR
            (building_id IS NULL AND campus_id IS NOT NULL AND floor_id IS NULL)
        );

CREATE INDEX obstacle_campus_idx ON obstacle (tenant_id, campus_id);
