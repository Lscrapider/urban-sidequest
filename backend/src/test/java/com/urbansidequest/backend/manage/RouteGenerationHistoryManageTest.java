package com.urbansidequest.backend.manage;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.RouteExecutionStatus;
import com.urbansidequest.backend.domain.enums.RouteRequestStatus;
import com.urbansidequest.backend.domain.vo.GeoPointVO;
import com.urbansidequest.backend.domain.vo.RouteAreaVO;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.mapper.RouteGenerationHistoryMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RouteGenerationHistoryManageTest {

    @Test
    void createsPendingHistoryWithEmptyRoutesAndQueuedStage() {
        Fixture fixture = fixture();
        RouteGenerationVO pending = pendingGeneration();

        fixture.manage().createPendingHistory(pending);

        verify(fixture.mapper()).insertPendingHistory(argThat(history ->
                history.getCandidateSetId().equals(pending.candidateSetId())
                        && history.getUserId().equals(pending.userId())
                        && history.getRouteCode() == null
                        && history.getRouteCount() == 0
                        && history.getGenerationStatus() == RouteRequestStatus.PENDING
                        && "queued".equals(history.getGenerationStage())
                        && history.getGenerationJson().contains("\"status\":\"PENDING\"")
        ));
    }

    @Test
    void deletesOnlyPendingHistoryForCandidateSetAndUser() {
        Fixture fixture = fixture();
        UUID candidateSetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        fixture.manage().deletePendingHistory(candidateSetId, userId);

        verify(fixture.mapper()).deletePendingHistory(candidateSetId, userId);
    }

    private static Fixture fixture() {
        RouteGenerationHistoryMapper mapper = mock(RouteGenerationHistoryMapper.class);
        RouteGenerationHistoryManage manage = new RouteGenerationHistoryManage(new ObjectMapper());
        ReflectionTestUtils.setField(manage, "baseMapper", mapper);
        return new Fixture(manage, mapper);
    }

    private static RouteGenerationVO pendingGeneration() {
        return new RouteGenerationVO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                RouteRequestStatus.PENDING,
                new RouteAreaVO(
                        AreaMode.AUTO_RADIUS,
                        "测试区域",
                        "北京市",
                        new GeoPointVO(BigDecimal.valueOf(116.397), BigDecimal.valueOf(39.908)),
                        3000,
                        List.of(),
                        "正在生成路线"
                ),
                List.of(),
                List.of(),
                "queued",
                null,
                RouteExecutionStatus.GENERATED
        );
    }

    private record Fixture(RouteGenerationHistoryManage manage, RouteGenerationHistoryMapper mapper) {
    }
}
