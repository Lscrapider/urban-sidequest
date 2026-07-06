CREATE INDEX idx_poi_cache_location_gcj02 ON poi_cache USING GIST (location_gcj02);

CREATE INDEX idx_poi_cache_city_code ON poi_cache (city_code);

CREATE INDEX idx_route_requests_user_id ON route_requests (user_id);

CREATE INDEX idx_route_requests_status ON route_requests (status);

CREATE INDEX idx_route_requests_area_polygon_gcj02 ON route_requests USING GIST (area_polygon_gcj02);

CREATE INDEX idx_generated_routes_request_id ON generated_routes (request_id);

CREATE INDEX idx_generated_routes_polyline_gcj02 ON generated_routes USING GIST (polyline_gcj02);

CREATE INDEX idx_route_stops_route_id ON route_stops (route_id);

CREATE INDEX idx_route_stops_location_gcj02 ON route_stops USING GIST (location_gcj02);

CREATE INDEX idx_route_checkins_user_id ON route_checkins (user_id);

CREATE INDEX idx_route_checkins_route_id ON route_checkins (route_id);

CREATE INDEX idx_route_feedback_user_id ON route_feedback (user_id);

CREATE INDEX idx_route_feedback_route_id ON route_feedback (route_id);

CREATE INDEX idx_interest_tag_catalog_enabled ON interest_tag_catalog (enabled);

CREATE INDEX idx_interest_tag_catalog_sort_order ON interest_tag_catalog (sort_order);

CREATE INDEX idx_interest_tag_catalog_parent ON interest_tag_catalog (parent_tag_code);

CREATE INDEX idx_interest_tag_catalog_selectable ON interest_tag_catalog (enabled, selectable);

CREATE INDEX idx_poi_recall_plan_config_tag
    ON poi_recall_plan_config (enabled, plan_type, tag_code);

CREATE INDEX idx_poi_recall_plan_config_type
    ON poi_recall_plan_config (enabled, plan_type, priority);

CREATE INDEX idx_poi_semantic_mapping_enabled_priority
    ON poi_semantic_mapping (enabled, priority);

CREATE INDEX idx_poi_semantic_mapping_category_group
    ON poi_semantic_mapping (category_group);

CREATE INDEX idx_route_segment_cost_cache_origin_destination
    ON route_segment_cost_cache (origin_poi_id, destination_poi_id, mode);

CREATE INDEX idx_route_segment_cost_cache_coordinate_mode_fetched_at
    ON route_segment_cost_cache (
        origin_longitude_gcj02,
        origin_latitude_gcj02,
        destination_longitude_gcj02,
        destination_latitude_gcj02,
        mode,
        fetched_at DESC
    );

CREATE INDEX idx_amap_poi_search_cache_expires_at
    ON amap_poi_search_cache (expires_at);

CREATE INDEX idx_user_preference_profiles_user_id
    ON user_preference_profiles (user_id);

CREATE INDEX idx_user_preference_tag_affinities_user_id
    ON user_preference_tag_affinities (user_id);

CREATE INDEX idx_user_preference_tag_affinities_tag_code
    ON user_preference_tag_affinities (tag_code);

CREATE INDEX idx_route_generation_history_user_created_at
    ON route_generation_history (user_id, created_at DESC);

CREATE INDEX idx_route_generation_history_candidate_set
    ON route_generation_history (candidate_set_id);

CREATE INDEX idx_route_execution_user_updated_at
    ON route_execution (user_id, updated_at DESC);

CREATE INDEX idx_route_execution_candidate_set_updated_at
    ON route_execution (candidate_set_id, updated_at DESC);

CREATE UNIQUE INDEX uk_route_execution_user_in_progress
    ON route_execution (user_id)
    WHERE execution_status = 'IN_PROGRESS';

CREATE INDEX idx_route_interactions_user_id
    ON route_interactions (user_id);

CREATE INDEX idx_route_interactions_user_favorite
    ON route_interactions (user_id, favorite);

CREATE INDEX idx_route_preference_feedbacks_user_candidate_label
    ON route_preference_feedbacks (user_id, candidate_set_id, feedback_label);

CREATE INDEX idx_route_shares_created_at
    ON route_shares (created_at DESC);

CREATE INDEX idx_route_shares_user_created_at
    ON route_shares (user_id, created_at DESC);

CREATE INDEX idx_route_shares_candidate_set
    ON route_shares (candidate_set_id);

CREATE INDEX idx_baidu_poi_semantic_mapping_enabled_priority
    ON baidu_poi_semantic_mapping (enabled, priority);

CREATE INDEX idx_baidu_poi_semantic_mapping_category_group
    ON baidu_poi_semantic_mapping (category_group);
