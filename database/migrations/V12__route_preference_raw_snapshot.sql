CREATE TABLE route_preference_raw_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_set_id UUID NOT NULL,
    request_id UUID NOT NULL,
    user_id UUID REFERENCES users(id),
    raw_schema_version VARCHAR(64) NOT NULL,
    generate_param_json JSONB NOT NULL,
    area_json JSONB,
    weather_json JSONB NOT NULL,
    user_preference_profile_json JSONB NOT NULL,
    interest_tag_catalog_json JSONB NOT NULL,
    interest_tags_json JSONB NOT NULL,
    poi_semantic_mappings_json JSONB NOT NULL,
    poi_candidates_json JSONB NOT NULL,
    poi_linear_traces_json JSONB NOT NULL,
    selected_routes_json JSONB NOT NULL,
    segment_costs_json JSONB NOT NULL,
    warnings_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_route_preference_raw_snapshots_candidate_set UNIQUE (candidate_set_id)
);

CREATE INDEX idx_route_preference_raw_snapshots_request_id
    ON route_preference_raw_snapshots (request_id);

CREATE INDEX idx_route_preference_raw_snapshots_user_id
    ON route_preference_raw_snapshots (user_id);

CREATE INDEX idx_route_preference_raw_snapshots_raw_schema_version
    ON route_preference_raw_snapshots (raw_schema_version);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'urban_sidequest') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON route_preference_raw_snapshots TO urban_sidequest;
    END IF;
END $$;
