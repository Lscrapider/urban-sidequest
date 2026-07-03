CREATE TABLE route_generation_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    candidate_set_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    area_label VARCHAR(256) NOT NULL,
    route_count INTEGER NOT NULL DEFAULT 0,
    generation_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_route_generation_history_request UNIQUE (request_id)
);

CREATE INDEX idx_route_generation_history_user_created_at
    ON route_generation_history (user_id, created_at DESC);

CREATE INDEX idx_route_generation_history_candidate_set
    ON route_generation_history (candidate_set_id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'urban_sidequest') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON route_generation_history TO urban_sidequest;
    END IF;
END $$;
