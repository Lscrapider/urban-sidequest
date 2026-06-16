package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.RoutePlanDTO;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.domain.po.RouteSegmentCostCachePO;
import com.urbansidequest.backend.mapper.RouteSegmentCostCacheMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RouteSegmentCostCacheManage extends ServiceImpl<RouteSegmentCostCacheMapper, RouteSegmentCostCachePO> {

    private static final int COORDINATE_SCALE = 7;

    public Optional<String> findLatestRawPayload(
            GeoPointDTO origin,
            GeoPointDTO destination,
            SegmentTransportMode mode
    ) {
        return Optional.ofNullable(this.baseMapper.findLatestRawPayload(
                this.normalize(origin.longitudeGcj02()),
                this.normalize(origin.latitudeGcj02()),
                this.normalize(destination.longitudeGcj02()),
                this.normalize(destination.latitudeGcj02()),
                mode.name()
        ));
    }

    public void saveRawPayload(
            GeoPointDTO origin,
            GeoPointDTO destination,
            SegmentTransportMode mode,
            RoutePlanDTO plan
    ) {
        if (plan.rawPayload() == null || plan.rawPayload().isBlank()) {
            return;
        }
        this.baseMapper.insertRawPayload(
                this.normalize(origin.longitudeGcj02()),
                this.normalize(origin.latitudeGcj02()),
                this.normalize(destination.longitudeGcj02()),
                this.normalize(destination.latitudeGcj02()),
                mode.name(),
                plan.distanceMeters(),
                plan.durationMinutes() * 60,
                plan.rawPayload()
        );
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
    }
}
