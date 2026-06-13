package com.urbansidequest.backend.handler.route.segment;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.SegmentCostDTO;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.service.route.RouteGenerationContext;

public interface SegmentCostStrategy {

    boolean supports(SegmentTransportMode mode);

    SegmentCostDTO calculate(PoiCandidateDTO origin, PoiCandidateDTO destination, RouteGenerationContext context);
}
