package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.urbansidequest.backend.domain.po.AmapPoiSearchCachePO;
import com.urbansidequest.backend.mapper.AmapPoiSearchCacheMapper;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AmapPoiSearchCacheManage extends ServiceImpl<AmapPoiSearchCacheMapper, AmapPoiSearchCachePO> {

    public Optional<String> findValidResponseJson(
            String searchType,
            String areaHash,
            String typesHash,
            String keywordsHash,
            int pageNum,
            int pageSize
    ) {
        return Optional.ofNullable(this.baseMapper.findValidResponseJson(
                searchType,
                areaHash,
                typesHash,
                keywordsHash,
                pageNum,
                pageSize
        ));
    }

    public void upsertResponseJson(
            String searchType,
            String areaHash,
            String typesHash,
            String keywordsHash,
            int pageNum,
            int pageSize,
            String requestParamsJson,
            String responseJson,
            int poiCount,
            Instant expiresAt
    ) {
        this.baseMapper.upsertResponseJson(
                searchType,
                areaHash,
                typesHash,
                keywordsHash,
                pageNum,
                pageSize,
                requestParamsJson,
                responseJson,
                poiCount,
                expiresAt
        );
    }
}
