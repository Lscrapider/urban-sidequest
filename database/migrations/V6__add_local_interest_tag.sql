-- Linear Ranker v1 前置数据修正：
-- V4 语义映射和 V5 用户画像均会引用 LOCAL，必须先补齐统一的 interest_tag_catalog seed。
INSERT INTO interest_tag_catalog (
    tag_code,
    display_name,
    amap_type_codes,
    amap_keywords,
    category_group,
    sort_order
)
VALUES (
    'LOCAL',
    '本地生活',
    ARRAY['050000', '060000', '070000'],
    ARRAY['本地菜', '老字号', '夜市', '市集', '老街', '街区'],
    'LOCAL',
    80
)
ON CONFLICT (tag_code) DO UPDATE
SET display_name = EXCLUDED.display_name,
    amap_type_codes = EXCLUDED.amap_type_codes,
    amap_keywords = EXCLUDED.amap_keywords,
    category_group = EXCLUDED.category_group,
    sort_order = EXCLUDED.sort_order,
    enabled = TRUE,
    updated_at = now();

UPDATE poi_semantic_mapping
SET interest_tag_codes = interest_tag_codes || ARRAY['LOCAL']::TEXT[],
    updated_at = now()
WHERE mapping_code = 'NIGHT_MARKET_VIEW'
  AND NOT ('LOCAL' = ANY(interest_tag_codes));
