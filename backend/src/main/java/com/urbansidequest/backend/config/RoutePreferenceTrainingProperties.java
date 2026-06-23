package com.urbansidequest.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "route.preference.training")
public class RoutePreferenceTrainingProperties {

    private boolean rawSnapshotEnabled = true;

    public boolean isRawSnapshotEnabled() {
        return this.rawSnapshotEnabled;
    }

    public void setRawSnapshotEnabled(boolean rawSnapshotEnabled) {
        this.rawSnapshotEnabled = rawSnapshotEnabled;
    }
}
