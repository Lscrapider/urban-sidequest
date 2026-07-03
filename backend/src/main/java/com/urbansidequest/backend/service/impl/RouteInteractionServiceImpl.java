package com.urbansidequest.backend.service.impl;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.enums.RouteInteractionReaction;
import com.urbansidequest.backend.domain.enums.RoutePreferenceFeedbackLabel;
import com.urbansidequest.backend.domain.param.RouteInteractionParam;
import com.urbansidequest.backend.domain.po.RouteGenerationHistoryPO;
import com.urbansidequest.backend.domain.po.RouteInteractionPO;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteInteractionVO;
import com.urbansidequest.backend.manage.RouteGenerationHistoryManage;
import com.urbansidequest.backend.manage.RouteInteractionManage;
import com.urbansidequest.backend.manage.RoutePreferenceFeedbackManage;
import com.urbansidequest.backend.service.RouteInteractionService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RouteInteractionServiceImpl implements RouteInteractionService {

    private final RouteInteractionManage routeInteractionManage;

    private final RouteGenerationHistoryManage routeGenerationHistoryManage;

    private final RoutePreferenceFeedbackManage routePreferenceFeedbackManage;

    public RouteInteractionServiceImpl(
            RouteInteractionManage routeInteractionManage,
            RouteGenerationHistoryManage routeGenerationHistoryManage,
            RoutePreferenceFeedbackManage routePreferenceFeedbackManage
    ) {
        this.routeInteractionManage = routeInteractionManage;
        this.routeGenerationHistoryManage = routeGenerationHistoryManage;
        this.routePreferenceFeedbackManage = routePreferenceFeedbackManage;
    }

    @Override
    public List<RouteInteractionVO> listInteractions(AuthenticatedUser authenticatedUser) {
        return this.routeInteractionManage.findByUserId(authenticatedUser.id())
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public RouteInteractionVO saveInteraction(
            AuthenticatedUser authenticatedUser,
            UUID requestId,
            String routeCode,
            RouteInteractionParam param
    ) {
        UUID candidateSetId = this.validateRoute(authenticatedUser.id(), requestId, routeCode);
        boolean favorite = param.getFavorite() != null && param.getFavorite();
        RoutePreferenceFeedbackLabel feedbackLabel = this.resolveFeedbackLabel(favorite, param.getReaction());
        this.routeInteractionManage.upsert(
                authenticatedUser.id(),
                candidateSetId,
                routeCode,
                favorite,
                param.getReaction()
        );
        this.routePreferenceFeedbackManage.upsert(authenticatedUser.id(), candidateSetId, routeCode, feedbackLabel);
        return new RouteInteractionVO(
                candidateSetId,
                routeCode,
                favorite,
                param.getReaction() == null ? null : param.getReaction().name()
        );
    }

    private RoutePreferenceFeedbackLabel resolveFeedbackLabel(boolean favorite, RouteInteractionReaction reaction) {
        if (reaction == RouteInteractionReaction.DISLIKED) {
            return RoutePreferenceFeedbackLabel.REJECT;
        }
        if (favorite || reaction == RouteInteractionReaction.LIKED) {
            return RoutePreferenceFeedbackLabel.CHOOSE;
        }
        return null;
    }

    private UUID validateRoute(UUID userId, UUID requestId, String routeCode) {
        RouteGenerationHistoryPO history = this.routeGenerationHistoryManage.findByUserAndRequestId(userId, requestId)
                .orElseThrow(() -> new IllegalArgumentException("路线历史不存在"));
        RouteGenerationVO routeGeneration = this.routeGenerationHistoryManage.toRouteGenerationVO(history);
        boolean routeExists = routeGeneration.routes().stream()
                .anyMatch(route -> route.routeCode().equals(routeCode));
        if (!routeExists) {
            throw new IllegalArgumentException("路线不属于当前历史记录");
        }
        return history.getCandidateSetId();
    }

    private RouteInteractionVO toVO(RouteInteractionPO interaction) {
        return new RouteInteractionVO(
                interaction.getCandidateSetId(),
                interaction.getRouteCode(),
                Boolean.TRUE.equals(interaction.getFavorite()),
                interaction.getReaction() == null ? null : interaction.getReaction().name()
        );
    }
}
