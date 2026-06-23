package com.urbansidequest.backend.handler.route.training;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearTraceDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.dto.RouteWeatherDTO;
import com.urbansidequest.backend.domain.dto.SegmentCostDTO;
import com.urbansidequest.backend.domain.dto.UserPreferenceProfileDTO;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import com.urbansidequest.backend.domain.po.PoiSemanticMappingPO;
import java.util.List;
import java.util.UUID;

public record RoutePreferenceRawSnapshotPayload(
        UUID candidateSetId,
        UUID requestId,
        UUID userId,
        String rawSchemaVersion,
        RouteGenerateParam generateParam,
        RouteAreaDTO area,
        RouteWeatherDTO weather,
        UserPreferenceProfileDTO userPreferenceProfile,
        List<InterestTagCatalogPO> interestTagCatalog,
        List<InterestTagCatalogPO> interestTags,
        List<PoiSemanticMappingPO> poiSemanticMappings,
        List<PoiCandidateDTO> poiCandidates,
        List<PoiLinearTraceDTO> poiLinearTraces,
        List<CandidateRouteDTO> selectedRoutes,
        List<SegmentCostDTO> segmentCosts,
        List<String> warnings
) {

    public RoutePreferenceRawSnapshotPayload {
        rawSchemaVersion = rawSchemaVersion == null ? RoutePreferenceRawSnapshotSchema.VERSION : rawSchemaVersion;
        weather = weather == null ? RouteWeatherDTO.unavailable() : weather;
        userPreferenceProfile = userPreferenceProfile == null ? UserPreferenceProfileDTO.empty() : userPreferenceProfile;
        interestTagCatalog = interestTagCatalog == null ? List.of() : List.copyOf(interestTagCatalog);
        interestTags = interestTags == null ? List.of() : List.copyOf(interestTags);
        poiSemanticMappings = poiSemanticMappings == null ? List.of() : List.copyOf(poiSemanticMappings);
        poiCandidates = poiCandidates == null ? List.of() : List.copyOf(poiCandidates);
        poiLinearTraces = poiLinearTraces == null ? List.of() : List.copyOf(poiLinearTraces);
        selectedRoutes = selectedRoutes == null ? List.of() : List.copyOf(selectedRoutes);
        segmentCosts = segmentCosts == null ? List.of() : List.copyOf(segmentCosts);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
