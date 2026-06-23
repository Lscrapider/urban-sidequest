package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.po.RoutePreferenceRawSnapshotPO;
import com.urbansidequest.backend.handler.route.training.RoutePreferenceRawSnapshotPayload;
import com.urbansidequest.backend.mapper.RoutePreferenceRawSnapshotMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RoutePreferenceRawSnapshotManage extends ServiceImpl<RoutePreferenceRawSnapshotMapper, RoutePreferenceRawSnapshotPO> {

    private final ObjectMapper objectMapper;

    public RoutePreferenceRawSnapshotManage(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void upsertSnapshot(RoutePreferenceRawSnapshotPayload payload) {
        RoutePreferenceRawSnapshotPO snapshot = RoutePreferenceRawSnapshotPO.fromPayload(payload, this.objectMapper);
        this.baseMapper.upsertSnapshot(snapshot);
    }

    public Optional<RoutePreferenceRawSnapshotPO> findByCandidateSetId(UUID candidateSetId) {
        return Optional.ofNullable(this.baseMapper.selectByCandidateSetId(candidateSetId));
    }
}
