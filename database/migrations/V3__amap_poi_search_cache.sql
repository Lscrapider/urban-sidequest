CREATE TABLE amap_poi_search_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    search_type VARCHAR(32) NOT NULL,
    area_hash VARCHAR(128) NOT NULL,
    types_hash VARCHAR(128) NOT NULL,
    keywords_hash VARCHAR(128) NOT NULL,
    page_num INTEGER NOT NULL,
    page_size INTEGER NOT NULL,
    request_params_json JSONB NOT NULL,
    response_json JSONB NOT NULL,
    poi_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_amap_poi_search_cache_query UNIQUE (
        search_type,
        area_hash,
        types_hash,
        keywords_hash,
        page_num,
        page_size
    )
);

CREATE INDEX idx_amap_poi_search_cache_expires_at
    ON amap_poi_search_cache (expires_at);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'urban_sidequest') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON amap_poi_search_cache TO urban_sidequest;
    END IF;
END
$$;
