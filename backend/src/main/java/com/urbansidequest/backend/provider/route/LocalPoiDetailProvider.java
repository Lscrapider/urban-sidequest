package com.urbansidequest.backend.provider.route;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LocalPoiDetailProvider implements PoiDetailProvider {

    @Override
    public List<PoiCandidateDTO> enrichDetails(RouteGenerationContext context, List<PoiCandidateDTO> candidates) {
        return candidates;
    }
}
