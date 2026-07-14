CREATE TABLE administrative_regions (
    adcode VARCHAR(16) PRIMARY KEY,
    parent_adcode VARCHAR(16) REFERENCES administrative_regions(adcode),
    name VARCHAR(128) NOT NULL,
    level VARCHAR(24) NOT NULL,
    longitude_gcj02 NUMERIC(10, 7) NOT NULL,
    latitude_gcj02 NUMERIC(10, 7) NOT NULL,
    selectable BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    children_loaded BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_administrative_regions_parent_sort
    ON administrative_regions(parent_adcode, sort_order, name);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'urban_sidequest') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON administrative_regions TO urban_sidequest;
    END IF;
END $$;
