package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.urbansidequest.backend.domain.enums.RoutePreferenceSampleStatus;
import com.urbansidequest.backend.domain.po.RoutePreferenceTrainingSamplePO;
import com.urbansidequest.backend.handler.route.training.RouteInputFeatureSnapshot;
import com.urbansidequest.backend.mapper.RoutePreferenceTrainingSampleMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RoutePreferenceTrainingSampleManage extends ServiceImpl<RoutePreferenceTrainingSampleMapper, RoutePreferenceTrainingSamplePO> {

    public void upsertGeneratedSample(
            UUID candidateSetId,
            UUID requestId,
            UUID userId,
            String routeCode,
            RouteInputFeatureSnapshot snapshot
    ) {
        this.baseMapper.upsertSample(
                candidateSetId,
                requestId,
                userId,
                routeCode,
                snapshot.featureSchemaVersion(),
                snapshot.stopMatrixJson(),
                snapshot.segmentMatrixJson(),
                snapshot.routeDerivedVectorJson(),
                snapshot.contextCrossVectorJson(),
                snapshot.intraSetVectorJson(),
                snapshot.contextJson(),
                RoutePreferenceSampleStatus.GENERATED.name()
        );
    }

    public void markCandidateSetTrainReady(
            UUID candidateSetId,
            String labelSource,
            String labelJson,
            BigDecimal sampleWeight
    ) {
        this.baseMapper.markCandidateSetTrainReady(
                candidateSetId,
                labelSource,
                labelJson,
                sampleWeight,
                RoutePreferenceSampleStatus.TRAIN_READY.name()
        );
    }

    public void upsertRebuiltSample(
            UUID candidateSetId,
            UUID requestId,
            UUID userId,
            String routeCode,
            RouteInputFeatureSnapshot snapshot
    ) {
        this.baseMapper.upsertFeatureSnapshot(
                candidateSetId,
                requestId,
                userId,
                routeCode,
                snapshot.featureSchemaVersion(),
                snapshot.stopMatrixJson(),
                snapshot.segmentMatrixJson(),
                snapshot.routeDerivedVectorJson(),
                snapshot.contextCrossVectorJson(),
                snapshot.intraSetVectorJson(),
                snapshot.contextJson(),
                RoutePreferenceSampleStatus.GENERATED.name()
        );
    }

    public List<UUID> findOutdatedCandidateSetIds(String featureSchemaVersion) {
        return this.baseMapper.findOutdatedCandidateSetIds(featureSchemaVersion).stream()
                .map(UUID::fromString)
                .toList();
    }
}
