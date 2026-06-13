package com.urbansidequest.backend.service.route;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.dto.SegmentCostDTO;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RouteGenerationContext {

    private final UUID requestId;

    private final UUID userId;

    private final RouteGenerateParam generateParam;

    private RouteAreaDTO area;

    private List<InterestTagCatalogPO> interestTags = new ArrayList<>();

    private List<PoiCandidateDTO> poiCandidates = new ArrayList<>();

    private List<SegmentCostDTO> segmentCosts = new ArrayList<>();

    private List<CandidateRouteDTO> candidateRoutes = new ArrayList<>();

    private List<CandidateRouteDTO> selectedRoutes = new ArrayList<>();

    private final List<String> warnings = new ArrayList<>();

    public RouteGenerationContext(UUID requestId, UUID userId, RouteGenerateParam generateParam) {
        this.requestId = requestId;
        this.userId = userId;
        this.generateParam = generateParam;
    }

    public UUID getRequestId() {
        return this.requestId;
    }

    public UUID getUserId() {
        return this.userId;
    }

    public RouteGenerateParam getGenerateParam() {
        return this.generateParam;
    }

    public RouteAreaDTO getArea() {
        return this.area;
    }

    public void setArea(RouteAreaDTO area) {
        this.area = area;
    }

    public List<InterestTagCatalogPO> getInterestTags() {
        return this.interestTags;
    }

    public void setInterestTags(List<InterestTagCatalogPO> interestTags) {
        this.interestTags = interestTags;
    }

    public List<PoiCandidateDTO> getPoiCandidates() {
        return this.poiCandidates;
    }

    public void setPoiCandidates(List<PoiCandidateDTO> poiCandidates) {
        this.poiCandidates = poiCandidates;
    }

    public List<SegmentCostDTO> getSegmentCosts() {
        return this.segmentCosts;
    }

    public void setSegmentCosts(List<SegmentCostDTO> segmentCosts) {
        this.segmentCosts = segmentCosts;
    }

    public List<CandidateRouteDTO> getCandidateRoutes() {
        return this.candidateRoutes;
    }

    public void setCandidateRoutes(List<CandidateRouteDTO> candidateRoutes) {
        this.candidateRoutes = candidateRoutes;
    }

    public List<CandidateRouteDTO> getSelectedRoutes() {
        return this.selectedRoutes;
    }

    public void setSelectedRoutes(List<CandidateRouteDTO> selectedRoutes) {
        this.selectedRoutes = selectedRoutes;
    }

    public List<String> getWarnings() {
        return this.warnings;
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }
}
