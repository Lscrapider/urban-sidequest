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

CREATE INDEX idx_route_preference_feedbacks_user_candidate_label
    ON route_preference_feedbacks (user_id, candidate_set_id, feedback_label);
