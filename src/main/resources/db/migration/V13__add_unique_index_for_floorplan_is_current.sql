-- Clean up any existing duplicate current floorplans, keeping only the most recently uploaded one.
WITH ranked_floorplans AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY floor_id ORDER BY uploaded_at DESC, id DESC) as rn
    FROM floorplan
    WHERE is_current = true
)
UPDATE floorplan
SET is_current = false
WHERE id IN (
    SELECT id FROM ranked_floorplans WHERE rn > 1
);

-- Prevent duplicate current floorplans per floor.
CREATE UNIQUE INDEX unique_floorplan_is_current ON floorplan (floor_id) WHERE (is_current = true);
