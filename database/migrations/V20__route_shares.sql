CREATE TABLE route_shares (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    request_id UUID NOT NULL REFERENCES route_generation_history(request_id),
    route_code VARCHAR(16) NOT NULL,
    share_text VARCHAR(240) NOT NULL,
    image_url TEXT NOT NULL,
    image_object_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_route_shares_user_route UNIQUE (user_id, request_id, route_code)
);

CREATE INDEX idx_route_shares_created_at
    ON route_shares (created_at DESC);

CREATE INDEX idx_route_shares_user_created_at
    ON route_shares (user_id, created_at DESC);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'urban_sidequest') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON route_shares TO urban_sidequest;
    END IF;
END $$;
