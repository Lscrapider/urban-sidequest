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

CREATE INDEX idx_route_interactions_user_id
    ON route_interactions (user_id);

CREATE INDEX idx_route_interactions_user_favorite
    ON route_interactions (user_id, favorite);
