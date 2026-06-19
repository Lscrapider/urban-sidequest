CREATE TABLE route_preference_training_samples (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_set_id UUID NOT NULL,
    request_id UUID NOT NULL,
    user_id UUID REFERENCES users(id),
    route_code VARCHAR(16) NOT NULL,
    generated_route_id UUID REFERENCES generated_routes(id),
    feature_schema_version VARCHAR(64) NOT NULL,
    stop_matrix_json JSONB NOT NULL,
    segment_matrix_json JSONB NOT NULL,
    route_derived_vector_json JSONB NOT NULL,
    context_cross_vector_json JSONB,
    context_json JSONB NOT NULL,
    label_source VARCHAR(64),
    label_json JSONB,
    sample_weight NUMERIC(8, 4),
    sample_status VARCHAR(32) NOT NULL DEFAULT 'GENERATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_route_preference_sample_set_code UNIQUE (candidate_set_id, route_code)
);

CREATE INDEX idx_route_preference_training_samples_candidate_set_id
    ON route_preference_training_samples (candidate_set_id);

CREATE INDEX idx_route_preference_training_samples_request_id
    ON route_preference_training_samples (request_id);

CREATE INDEX idx_route_preference_training_samples_status
    ON route_preference_training_samples (sample_status);

CREATE TABLE route_preference_judgments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_set_id UUID NOT NULL,
    judge_type VARCHAR(64) NOT NULL,
    judge_model VARCHAR(128),
    judge_prompt_version VARCHAR(64),
    ranking_json JSONB NOT NULL,
    accepted_route_codes_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    rejected_route_codes_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    reason_codes_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    confidence NUMERIC(4, 3),
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_route_preference_judgments_candidate_set_id
    ON route_preference_judgments (candidate_set_id);

CREATE INDEX idx_route_preference_judgments_judge_type
    ON route_preference_judgments (judge_type);
