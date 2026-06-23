package com.urbansidequest.backend.service;

import java.util.UUID;

public interface RoutePreferenceFeatureRebuildService {

    int rebuildByCandidateSetId(UUID candidateSetId);

    int rebuildOutdatedSamples();
}
