ALTER TABLE route_preference_training_samples
    ADD COLUMN IF NOT EXISTS intra_set_vector_json JSONB;
