DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'urban_sidequest') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON amap_poi_search_cache TO urban_sidequest;
        GRANT SELECT, INSERT, UPDATE, DELETE ON route_generation_history TO urban_sidequest;
        GRANT SELECT, INSERT, UPDATE, DELETE ON route_execution TO urban_sidequest;
        GRANT SELECT, INSERT, UPDATE, DELETE ON route_interactions TO urban_sidequest;
        GRANT SELECT, INSERT, UPDATE, DELETE ON route_preference_feedbacks TO urban_sidequest;
        GRANT SELECT, INSERT, UPDATE, DELETE ON route_shares TO urban_sidequest;
        GRANT SELECT, INSERT, UPDATE, DELETE ON user_preference_profiles TO urban_sidequest;
        GRANT SELECT, INSERT, UPDATE, DELETE ON user_preference_tag_affinities TO urban_sidequest;
        GRANT SELECT ON interest_tag_catalog TO urban_sidequest;
        GRANT SELECT ON poi_recall_plan_config TO urban_sidequest;
        GRANT SELECT ON poi_semantic_mapping TO urban_sidequest;
        GRANT SELECT ON baidu_poi_semantic_mapping TO urban_sidequest;
    END IF;
END $$;
