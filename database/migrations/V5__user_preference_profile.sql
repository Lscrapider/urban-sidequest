DROP TABLE IF EXISTS user_preferences;

CREATE TABLE user_preference_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    distance_sensitivity NUMERIC(3, 2) NOT NULL DEFAULT 0,
    budget_sensitivity NUMERIC(3, 2) NOT NULL DEFAULT 0,
    transfer_sensitivity NUMERIC(3, 2) NOT NULL DEFAULT 0,
    hidden_gem_affinity NUMERIC(3, 2) NOT NULL DEFAULT 0,
    profile_confidence NUMERIC(3, 2) NOT NULL DEFAULT 0,
    questionnaire_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_preference_profiles_user UNIQUE (user_id),
    CONSTRAINT ck_user_preference_profiles_ratio CHECK (
        distance_sensitivity BETWEEN 0 AND 1
        AND budget_sensitivity BETWEEN 0 AND 1
        AND transfer_sensitivity BETWEEN 0 AND 1
        AND hidden_gem_affinity BETWEEN 0 AND 1
        AND profile_confidence BETWEEN 0 AND 1
    )
);

CREATE INDEX idx_user_preference_profiles_user_id
    ON user_preference_profiles (user_id);

CREATE TABLE user_preference_tag_affinities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    tag_code VARCHAR(64) NOT NULL REFERENCES interest_tag_catalog(tag_code),
    affinity NUMERIC(3, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_preference_tag_affinities_user_tag UNIQUE (user_id, tag_code),
    CONSTRAINT ck_user_preference_tag_affinities_affinity CHECK (affinity BETWEEN 0 AND 1)
);

CREATE INDEX idx_user_preference_tag_affinities_user_id
    ON user_preference_tag_affinities (user_id);

CREATE INDEX idx_user_preference_tag_affinities_tag_code
    ON user_preference_tag_affinities (tag_code);
