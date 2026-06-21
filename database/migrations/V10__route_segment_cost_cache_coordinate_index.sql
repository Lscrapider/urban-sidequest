-- 对齐 RouteSegmentCostCacheMapper 中按坐标 + mode + fetched_at 的缓存查询。
CREATE INDEX IF NOT EXISTS idx_route_segment_cost_cache_coordinate_mode_fetched_at
    ON route_segment_cost_cache (
        origin_longitude_gcj02,
        origin_latitude_gcj02,
        destination_longitude_gcj02,
        destination_latitude_gcj02,
        mode,
        fetched_at DESC
    );
