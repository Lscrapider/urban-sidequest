package com.urbansidequest.backend.handler.route.context;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearTraceDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.dto.SegmentCostDTO;
import com.urbansidequest.backend.domain.dto.RouteWeatherDTO;
import com.urbansidequest.backend.domain.dto.UserPreferenceProfileDTO;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import com.urbansidequest.backend.domain.po.PoiSemanticMappingPO;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RouteGenerationContext {

    private final UUID requestId;

    private final UUID candidateSetId;

    private final UUID userId;

    private final RouteGenerateParam generateParam;

    private RouteAreaDTO area;

    private RouteWeatherDTO routeWeather = RouteWeatherDTO.unavailable();

    private UserPreferenceProfileDTO userPreferenceProfile = UserPreferenceProfileDTO.empty();

    private List<InterestTagCatalogPO> interestTagCatalog = new ArrayList<>();

    private List<InterestTagCatalogPO> interestTags = new ArrayList<>();

    private List<PoiSemanticMappingPO> poiSemanticMappings = new ArrayList<>();

    private List<PoiCandidateDTO> poiCandidates = new ArrayList<>();

    private boolean transportSignalAvailable = true;

    private List<PoiLinearTraceDTO> poiLinearTraces = new ArrayList<>();

    private List<SegmentCostDTO> segmentCosts = new ArrayList<>();

    private List<CandidateRouteDTO> candidateRoutes = new ArrayList<>();

    private List<CandidateRouteDTO> selectedRoutes = new ArrayList<>();

    private final List<String> warnings = new ArrayList<>();

    public RouteGenerationContext(UUID requestId, UUID userId, RouteGenerateParam generateParam) {
        this.requestId = requestId;
        this.candidateSetId = UUID.randomUUID();
        this.userId = userId;
        this.generateParam = generateParam;
    }

    public UUID getRequestId() {
        return this.requestId;
    }

    public UUID getCandidateSetId() {
        return this.candidateSetId;
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

    public RouteWeatherDTO getRouteWeather() {
        return this.routeWeather;
    }

    public void setRouteWeather(RouteWeatherDTO routeWeather) {
        this.routeWeather = routeWeather == null ? RouteWeatherDTO.unavailable() : routeWeather;
    }

    public UserPreferenceProfileDTO getUserPreferenceProfile() {
        return this.userPreferenceProfile;
    }

    public void setUserPreferenceProfile(UserPreferenceProfileDTO userPreferenceProfile) {
        this.userPreferenceProfile = userPreferenceProfile == null ? UserPreferenceProfileDTO.empty() : userPreferenceProfile;
    }

    public List<InterestTagCatalogPO> getInterestTagCatalog() {
        return this.interestTagCatalog;
    }

    public void setInterestTagCatalog(List<InterestTagCatalogPO> interestTagCatalog) {
        this.interestTagCatalog = interestTagCatalog == null ? new ArrayList<>() : new ArrayList<>(interestTagCatalog);
    }

    public List<InterestTagCatalogPO> getInterestTags() {
        return this.interestTags;
    }

    public void setInterestTags(List<InterestTagCatalogPO> interestTags) {
        this.interestTags = interestTags;
    }

    public List<PoiSemanticMappingPO> getPoiSemanticMappings() {
        return this.poiSemanticMappings;
    }

    public void setPoiSemanticMappings(List<PoiSemanticMappingPO> poiSemanticMappings) {
        this.poiSemanticMappings = poiSemanticMappings;
    }

    public List<PoiCandidateDTO> getPoiCandidates() {
        return this.poiCandidates;
    }

    public void setPoiCandidates(List<PoiCandidateDTO> poiCandidates) {
        this.poiCandidates = poiCandidates;
    }

    public boolean isTransportSignalAvailable() {
        return this.transportSignalAvailable;
    }

    public void setTransportSignalAvailable(boolean transportSignalAvailable) {
        this.transportSignalAvailable = transportSignalAvailable;
    }

    public List<PoiLinearTraceDTO> getPoiLinearTraces() {
        return this.poiLinearTraces;
    }

    public void setPoiLinearTraces(List<PoiLinearTraceDTO> poiLinearTraces) {
        this.poiLinearTraces = poiLinearTraces == null ? new ArrayList<>() : new ArrayList<>(poiLinearTraces);
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
