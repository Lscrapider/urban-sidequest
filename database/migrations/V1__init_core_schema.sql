CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(32) NOT NULL UNIQUE,
    nickname VARCHAR(64),
    avatar_url TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    default_transport_modes TEXT[] NOT NULL DEFAULT ARRAY['WALK', 'SUBWAY'],
    default_route_goal VARCHAR(64) NOT NULL DEFAULT 'STEADY',
    interest_tags TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_preferences_user UNIQUE (user_id)
);

CREATE TABLE poi_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    amap_poi_id VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(256) NOT NULL,
    type_code VARCHAR(64),
    address TEXT,
    city_code VARCHAR(32),
    longitude_gcj02 NUMERIC(10, 7) NOT NULL,
    latitude_gcj02 NUMERIC(10, 7) NOT NULL,
    location_gcj02 GEOMETRY(Point, 4326) NOT NULL,
    amap_rating NUMERIC(3, 1),
    avg_price_cent INTEGER,
    raw_payload JSONB,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_poi_cache_location_gcj02 ON poi_cache USING GIST (location_gcj02);
CREATE INDEX idx_poi_cache_city_code ON poi_cache (city_code);

CREATE TABLE route_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    area_polygon_gcj02 GEOMETRY(Polygon, 4326),
    start_name VARCHAR(256),
    start_longitude_gcj02 NUMERIC(10, 7),
    start_latitude_gcj02 NUMERIC(10, 7),
    departure_time TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL,
    transport_modes TEXT[] NOT NULL,
    route_goal VARCHAR(64) NOT NULL,
    interest_tags TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    must_visit_poi_ids TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_route_requests_user_id ON route_requests (user_id);
CREATE INDEX idx_route_requests_status ON route_requests (status);
CREATE INDEX idx_route_requests_area_polygon_gcj02 ON route_requests USING GIST (area_polygon_gcj02);

CREATE TABLE generated_routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES route_requests(id),
    route_code VARCHAR(8) NOT NULL,
    title VARCHAR(128) NOT NULL,
    summary TEXT,
    total_duration_minutes INTEGER NOT NULL,
    total_distance_meters INTEGER,
    budget_cent INTEGER,
    risk_level VARCHAR(32) NOT NULL DEFAULT 'LOW',
    explanation TEXT,
    polyline_gcj02 GEOMETRY(LineString, 4326),
    status VARCHAR(32) NOT NULL DEFAULT 'GENERATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_generated_routes_request_code UNIQUE (request_id, route_code)
);

CREATE INDEX idx_generated_routes_request_id ON generated_routes (request_id);
CREATE INDEX idx_generated_routes_polyline_gcj02 ON generated_routes USING GIST (polyline_gcj02);

CREATE TABLE route_stops (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id UUID NOT NULL REFERENCES generated_routes(id),
    poi_id UUID REFERENCES poi_cache(id),
    stop_order INTEGER NOT NULL,
    name VARCHAR(256) NOT NULL,
    category VARCHAR(64),
    arrival_time TIMESTAMPTZ,
    stay_minutes INTEGER,
    transport_to_next VARCHAR(64),
    distance_to_next_meters INTEGER,
    duration_to_next_minutes INTEGER,
    reason TEXT,
    risk_note TEXT,
    longitude_gcj02 NUMERIC(10, 7) NOT NULL,
    latitude_gcj02 NUMERIC(10, 7) NOT NULL,
    location_gcj02 GEOMETRY(Point, 4326) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_route_stops_route_order UNIQUE (route_id, stop_order)
);

CREATE INDEX idx_route_stops_route_id ON route_stops (route_id);
CREATE INDEX idx_route_stops_location_gcj02 ON route_stops USING GIST (location_gcj02);

CREATE TABLE favorite_routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    route_id UUID NOT NULL REFERENCES generated_routes(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_favorite_routes_user_route UNIQUE (user_id, route_id)
);

CREATE TABLE favorite_places (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    poi_id UUID NOT NULL REFERENCES poi_cache(id),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_favorite_places_user_poi UNIQUE (user_id, poi_id)
);

CREATE TABLE route_checkins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    route_id UUID NOT NULL REFERENCES generated_routes(id),
    stop_id UUID REFERENCES route_stops(id),
    checkin_type VARCHAR(32) NOT NULL,
    note TEXT,
    photo_object_key TEXT,
    longitude_gcj02 NUMERIC(10, 7),
    latitude_gcj02 NUMERIC(10, 7),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_route_checkins_user_id ON route_checkins (user_id);
CREATE INDEX idx_route_checkins_route_id ON route_checkins (route_id);

CREATE TABLE route_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    route_id UUID NOT NULL REFERENCES generated_routes(id),
    fun_score SMALLINT,
    pace_score SMALLINT,
    surprise_score SMALLINT,
    fatigue_score SMALLINT,
    photogenic_score SMALLINT,
    recommend BOOLEAN,
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_route_feedback_fun_score CHECK (fun_score IS NULL OR fun_score BETWEEN 1 AND 5),
    CONSTRAINT ck_route_feedback_pace_score CHECK (pace_score IS NULL OR pace_score BETWEEN 1 AND 5),
    CONSTRAINT ck_route_feedback_surprise_score CHECK (surprise_score IS NULL OR surprise_score BETWEEN 1 AND 5),
    CONSTRAINT ck_route_feedback_fatigue_score CHECK (fatigue_score IS NULL OR fatigue_score BETWEEN 1 AND 5),
    CONSTRAINT ck_route_feedback_photogenic_score CHECK (photogenic_score IS NULL OR photogenic_score BETWEEN 1 AND 5)
);

CREATE INDEX idx_route_feedback_user_id ON route_feedback (user_id);
CREATE INDEX idx_route_feedback_route_id ON route_feedback (route_id);
