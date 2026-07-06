CREATE EXTENSION IF NOT EXISTS postgis;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(32) NOT NULL UNIQUE,
    nickname VARCHAR(64),
    avatar_url TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    completed_route_count INTEGER NOT NULL DEFAULT 0,
    travel_distance_meters BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
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
    transport_profile VARCHAR(64),
    route_goal VARCHAR(64) NOT NULL,
    interest_tags TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    must_visit_poi_ids TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

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

CREATE TABLE interest_tag_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tag_code VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(64) NOT NULL,
    parent_tag_code VARCHAR(64),
    tag_level VARCHAR(32) NOT NULL DEFAULT 'ROOT',
    selectable BOOLEAN NOT NULL DEFAULT TRUE,
    max_sibling_selected INTEGER,
    rollup_tag_codes TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    amap_type_codes TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    amap_keywords TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    category_group VARCHAR(64),
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    catalog_version VARCHAR(32) NOT NULL DEFAULT 'tag_catalog_v1_1',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE poi_recall_plan_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_code VARCHAR(96) NOT NULL UNIQUE,
    plan_version VARCHAR(32) NOT NULL DEFAULT 'poi_recall_v1_1',
    plan_type VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    trigger_value VARCHAR(96),
    tag_code VARCHAR(64),
    amap_type_codes TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    amap_keywords TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    role_hint VARCHAR(32) NOT NULL DEFAULT 'ANCHOR',
    category_group_hint VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN',
    intent_tags TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    priority INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reason_seed VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE poi_semantic_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mapping_code VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    exact_typecodes TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    amap_type_prefixes TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    keyword_patterns TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    category_group VARCHAR(64),
    primary_category_group VARCHAR(64),
    interest_tag_codes TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    is_classic BOOLEAN NOT NULL DEFAULT FALSE,
    is_local BOOLEAN NOT NULL DEFAULT FALSE,
    is_photo_friendly BOOLEAN NOT NULL DEFAULT FALSE,
    is_night_friendly BOOLEAN NOT NULL DEFAULT FALSE,
    is_quiet BOOLEAN NOT NULL DEFAULT FALSE,
    is_hidden_gem BOOLEAN NOT NULL DEFAULT FALSE,
    meal_candidate BOOLEAN NOT NULL DEFAULT FALSE,
    rest_candidate BOOLEAN NOT NULL DEFAULT FALSE,
    local_experience_candidate BOOLEAN NOT NULL DEFAULT FALSE,
    weather_sensitivity NUMERIC(3, 2) NOT NULL DEFAULT 0,
    priority INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    mapping_version VARCHAR(32) NOT NULL DEFAULT 'poi_semantic_v1_1',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

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

CREATE TABLE user_preference_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    preference_version VARCHAR(64) NOT NULL DEFAULT 'linear_ranker_v1',
    total_feedback_count INTEGER NOT NULL DEFAULT 0,
    exploration_weight NUMERIC(5, 4) NOT NULL DEFAULT 0.2500,
    last_feedback_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_preference_profiles_user_version UNIQUE (user_id, preference_version)
);

CREATE TABLE user_preference_tag_affinities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    preference_version VARCHAR(64) NOT NULL DEFAULT 'linear_ranker_v1',
    tag_code VARCHAR(64) NOT NULL,
    score NUMERIC(8, 5) NOT NULL DEFAULT 0,
    positive_count INTEGER NOT NULL DEFAULT 0,
    negative_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_preference_tag_affinities_user_tag UNIQUE (user_id, preference_version, tag_code)
);

CREATE TABLE route_generation_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_set_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    area_label VARCHAR(256) NOT NULL,
    route_count INTEGER NOT NULL DEFAULT 0,
    generation_status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    generation_stage VARCHAR(64),
    route_code VARCHAR(16),
    route_index INTEGER,
    route_title VARCHAR(256),
    city_name VARCHAR(128),
    total_duration_minutes INTEGER,
    total_distance_meters INTEGER,
    risk_level VARCHAR(32),
    stop_count INTEGER,
    generation_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_route_generation_history_candidate_route UNIQUE (candidate_set_id, route_code)
);

CREATE TABLE route_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    candidate_set_id UUID NOT NULL,
    route_code VARCHAR(16) NOT NULL,
    execution_status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    map_snapshot_url TEXT,
    map_snapshot_object_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_route_execution_completed_at CHECK (
        execution_status <> 'COMPLETED' OR completed_at IS NOT NULL
    ),
    CONSTRAINT fk_route_execution_history_route
        FOREIGN KEY (candidate_set_id, route_code)
        REFERENCES route_generation_history (candidate_set_id, route_code)
);

CREATE TABLE route_interactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    candidate_set_id UUID NOT NULL,
    route_code VARCHAR(8) NOT NULL,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    reaction VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_route_interactions_user_route UNIQUE (user_id, candidate_set_id, route_code),
    CONSTRAINT ck_route_interactions_reaction CHECK (reaction IS NULL OR reaction IN ('LIKED', 'DISLIKED'))
);

CREATE TABLE route_preference_feedbacks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    candidate_set_id UUID NOT NULL,
    route_code VARCHAR(8) NOT NULL,
    feedback_label VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_route_preference_feedbacks_user_route UNIQUE (user_id, candidate_set_id, route_code),
    CONSTRAINT ck_route_preference_feedbacks_label CHECK (
        feedback_label IS NULL OR feedback_label IN ('CHOOSE', 'REJECT')
    )
);

CREATE TABLE route_shares (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    candidate_set_id UUID NOT NULL,
    route_code VARCHAR(16) NOT NULL,
    share_text VARCHAR(240) NOT NULL,
    image_url TEXT NOT NULL,
    image_object_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_route_shares_user_candidate_route UNIQUE (user_id, candidate_set_id, route_code),
    CONSTRAINT fk_route_shares_history_route
        FOREIGN KEY (candidate_set_id, route_code)
        REFERENCES route_generation_history (candidate_set_id, route_code)
);

CREATE TABLE baidu_poi_semantic_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mapping_code VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    baidu_primary_tags TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    baidu_secondary_tags TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    baidu_type_values TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    keyword_patterns TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    category_group VARCHAR(64),
    interest_tag_codes TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    is_classic BOOLEAN NOT NULL DEFAULT FALSE,
    is_local BOOLEAN NOT NULL DEFAULT FALSE,
    is_photo_friendly BOOLEAN NOT NULL DEFAULT FALSE,
    is_night_friendly BOOLEAN NOT NULL DEFAULT FALSE,
    is_quiet BOOLEAN NOT NULL DEFAULT FALSE,
    is_hidden_gem BOOLEAN NOT NULL DEFAULT FALSE,
    weather_sensitivity NUMERIC(3, 2) NOT NULL DEFAULT 0,
    priority INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
