DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'urban_sidequest') THEN
        GRANT SELECT ON poi_semantic_mapping TO urban_sidequest;
        GRANT SELECT, INSERT, UPDATE, DELETE ON user_preference_profiles TO urban_sidequest;
        GRANT SELECT, INSERT, UPDATE, DELETE ON user_preference_tag_affinities TO urban_sidequest;
    END IF;
END $$;
