package com.urbansidequest.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.urbansidequest.backend.domain.po.RouteExecutionPO;
import com.urbansidequest.backend.domain.vo.GeneratedRouteVO;
import com.urbansidequest.backend.manage.RouteExecutionManage;
import com.urbansidequest.backend.provider.route.share.RouteShareImageObjectStore;
import com.urbansidequest.backend.provider.route.share.RouteShareImageObjectStore.StoredRouteShareImage;
import com.urbansidequest.backend.provider.route.share.RouteStaticMapImageBuilder;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RouteMapSnapshotSupport {

    private final RouteStaticMapImageBuilder routeStaticMapImageBuilder;

    private final RouteShareImageObjectStore routeShareImageObjectStore;

    private final RouteExecutionManage routeExecutionManage;

    public RouteMapSnapshotSupport(
            RouteStaticMapImageBuilder routeStaticMapImageBuilder,
            RouteShareImageObjectStore routeShareImageObjectStore,
            RouteExecutionManage routeExecutionManage
    ) {
        this.routeStaticMapImageBuilder = routeStaticMapImageBuilder;
        this.routeShareImageObjectStore = routeShareImageObjectStore;
        this.routeExecutionManage = routeExecutionManage;
    }

    public StoredRouteShareImage ensureSnapshot(UUID userId, UUID candidateSetId, RouteExecutionPO execution, GeneratedRouteVO route) {
        if (StrUtil.isNotBlank(execution.getMapSnapshotUrl()) && StrUtil.isNotBlank(execution.getMapSnapshotObjectKey())) {
            return new StoredRouteShareImage(execution.getMapSnapshotObjectKey(), execution.getMapSnapshotUrl());
        }
        byte[] mapImageBytes = this.routeStaticMapImageBuilder.build(route);
        StoredRouteShareImage storedImage = this.routeShareImageObjectStore.putMapSnapshotImage(
                userId,
                candidateSetId,
                route.routeCode(),
                mapImageBytes
        );
        this.routeExecutionManage.saveMapSnapshot(
                userId,
                candidateSetId,
                route.routeCode(),
                storedImage.imageUrl(),
                storedImage.objectKey()
        );
        return storedImage;
    }
}
