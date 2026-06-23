package com.urbansidequest.backend.service.impl;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.po.RoutePreferenceRawSnapshotPO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.training.RouteInputFeatureExtractor;
import com.urbansidequest.backend.handler.route.training.RouteInputFeatureSnapshot;
import com.urbansidequest.backend.handler.route.training.RoutePreferenceFeatureSchema;
import com.urbansidequest.backend.handler.route.training.RoutePreferenceRawSnapshotRestorer;
import com.urbansidequest.backend.manage.RoutePreferenceRawSnapshotManage;
import com.urbansidequest.backend.manage.RoutePreferenceTrainingSampleManage;
import com.urbansidequest.backend.service.RoutePreferenceFeatureRebuildService;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RoutePreferenceFeatureRebuildServiceImpl implements RoutePreferenceFeatureRebuildService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoutePreferenceFeatureRebuildServiceImpl.class);

    private final RoutePreferenceRawSnapshotManage routePreferenceRawSnapshotManage;

    private final RoutePreferenceTrainingSampleManage routePreferenceTrainingSampleManage;

    private final RouteInputFeatureExtractor routeInputFeatureExtractor;

    private final RoutePreferenceRawSnapshotRestorer routePreferenceRawSnapshotRestorer;

    public RoutePreferenceFeatureRebuildServiceImpl(
            RoutePreferenceRawSnapshotManage routePreferenceRawSnapshotManage,
            RoutePreferenceTrainingSampleManage routePreferenceTrainingSampleManage,
            RouteInputFeatureExtractor routeInputFeatureExtractor,
            RoutePreferenceRawSnapshotRestorer routePreferenceRawSnapshotRestorer
    ) {
        this.routePreferenceRawSnapshotManage = routePreferenceRawSnapshotManage;
        this.routePreferenceTrainingSampleManage = routePreferenceTrainingSampleManage;
        this.routeInputFeatureExtractor = routeInputFeatureExtractor;
        this.routePreferenceRawSnapshotRestorer = routePreferenceRawSnapshotRestorer;
    }

    @Override
    public int rebuildByCandidateSetId(UUID candidateSetId) {
        Optional<RoutePreferenceRawSnapshotPO> rawSnapshot = this.routePreferenceRawSnapshotManage.findByCandidateSetId(candidateSetId);
        if (rawSnapshot.isEmpty()) {
            LOGGER.warn("路线偏好特征重建跳过，冻结快照不存在，candidateSetId={}", candidateSetId);
            return 0;
        }
        RouteGenerationContext context = this.routePreferenceRawSnapshotRestorer.restore(rawSnapshot.get());
        int rebuiltCount = 0;
        for (CandidateRouteDTO route : context.getSelectedRoutes()) {
            RouteInputFeatureSnapshot snapshot = this.routeInputFeatureExtractor.extract(route, context);
            this.routePreferenceTrainingSampleManage.upsertRebuiltSample(
                    context.getCandidateSetId(),
                    context.getRequestId(),
                    context.getUserId(),
                    route.routeCode(),
                    snapshot
            );
            rebuiltCount++;
        }
        return rebuiltCount;
    }

    @Override
    public int rebuildOutdatedSamples() {
        int rebuiltCount = 0;
        for (UUID candidateSetId : this.routePreferenceTrainingSampleManage.findOutdatedCandidateSetIds(RoutePreferenceFeatureSchema.VERSION)) {
            try {
                rebuiltCount += this.rebuildByCandidateSetId(candidateSetId);
            } catch (RuntimeException exception) {
                LOGGER.warn("路线偏好特征重建失败，candidateSetId={}", candidateSetId, exception);
            }
        }
        return rebuiltCount;
    }
}
