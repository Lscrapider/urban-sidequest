package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.urbansidequest.backend.domain.po.RouteSharePO;
import com.urbansidequest.backend.mapper.RouteShareMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RouteShareManage extends ServiceImpl<RouteShareMapper, RouteSharePO> {

    public List<RouteSharePO> findLatest(int pageSize) {
        return this.baseMapper.selectLatest(pageSize);
    }

    public Optional<RouteSharePO> findByShareId(UUID shareId) {
        return Optional.ofNullable(this.baseMapper.selectByShareId(shareId));
    }

    public RouteSharePO upsert(
            UUID userId,
            UUID candidateSetId,
            String routeCode,
            String shareText,
            String imageUrl,
            String imageObjectKey
    ) {
        int updatedCount = this.baseMapper.upsertShare(userId, candidateSetId, routeCode, shareText, imageUrl, imageObjectKey);
        if (updatedCount < 1) {
            throw new IllegalStateException("路线分享保存失败");
        }
        return Optional.ofNullable(this.baseMapper.selectByUserRoute(userId, candidateSetId, routeCode))
                .orElseThrow(() -> new IllegalStateException("路线分享读取失败"));
    }
}
