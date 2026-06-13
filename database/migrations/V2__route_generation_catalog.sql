CREATE TABLE interest_tag_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tag_code VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(64) NOT NULL,
    amap_type_codes TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    amap_keywords TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    category_group VARCHAR(64),
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_interest_tag_catalog_enabled ON interest_tag_catalog (enabled);
CREATE INDEX idx_interest_tag_catalog_sort_order ON interest_tag_catalog (sort_order);

ALTER TABLE route_requests
    ADD COLUMN IF NOT EXISTS transport_profile VARCHAR(64);

CREATE TABLE route_segment_cost_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    origin_poi_id UUID REFERENCES poi_cache(id),
    destination_poi_id UUID REFERENCES poi_cache(id),
    origin_longitude_gcj02 NUMERIC(10, 7) NOT NULL,
    origin_latitude_gcj02 NUMERIC(10, 7) NOT NULL,
    destination_longitude_gcj02 NUMERIC(10, 7) NOT NULL,
    destination_latitude_gcj02 NUMERIC(10, 7) NOT NULL,
    mode VARCHAR(64) NOT NULL,
    distance_meters INTEGER NOT NULL,
    duration_seconds INTEGER NOT NULL,
    walk_distance_meters INTEGER,
    transfer_count INTEGER,
    polyline_gcj02 GEOMETRY(LineString, 4326),
    raw_payload JSONB,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_route_segment_cost_cache_origin_destination
    ON route_segment_cost_cache (origin_poi_id, destination_poi_id, mode);

INSERT INTO interest_tag_catalog (tag_code, display_name, amap_type_codes, amap_keywords, category_group, sort_order)
VALUES
    ('FOOD', '美食', ARRAY['050000'], ARRAY['本地菜', '小吃'], 'FOOD', 10),
    ('COFFEE', '咖啡休息', ARRAY['050500'], ARRAY['咖啡'], 'FOOD', 20),
    ('MUSEUM', '展馆', ARRAY['140000'], ARRAY['博物馆', '展览馆', '美术馆'], 'CULTURE', 30),
    ('SCENIC', '景点', ARRAY['110000'], ARRAY['景点', '公园'], 'SCENIC', 40),
    ('PHOTO', '拍照', ARRAY['110000'], ARRAY['地标', '观景', '公园'], 'SCENIC', 50),
    ('SHOPPING', '购物', ARRAY['060000'], ARRAY['商场', '街区'], 'SHOPPING', 60),
    ('NIGHT', '夜游', ARRAY['110000', '050000'], ARRAY['夜景', '夜市'], 'NIGHT', 70)
ON CONFLICT (tag_code) DO NOTHING;
