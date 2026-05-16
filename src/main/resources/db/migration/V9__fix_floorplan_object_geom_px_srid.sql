-- Specify the pixel coordinate SRID for editable floorplan object geometry.

ALTER TABLE floorplan_object
    ALTER COLUMN geom_px TYPE geometry(Geometry, 0)
    USING ST_SetSRID(geom_px, 0)::geometry(Geometry, 0);
