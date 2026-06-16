package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("amap_poi_search_cache")
public class AmapPoiSearchCachePO {

    @TableId("id")
    private UUID id;

    @TableField("search_type")
    private String searchType;

    @TableField("area_hash")
    private String areaHash;

    @TableField("types_hash")
    private String typesHash;

    @TableField("keywords_hash")
    private String keywordsHash;

    @TableField("page_num")
    private Integer pageNum;

    @TableField("page_size")
    private Integer pageSize;

    @TableField("request_params_json")
    private String requestParamsJson;

    @TableField("response_json")
    private String responseJson;

    @TableField("poi_count")
    private Integer poiCount;

    @TableField("expires_at")
    private Instant expiresAt;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
