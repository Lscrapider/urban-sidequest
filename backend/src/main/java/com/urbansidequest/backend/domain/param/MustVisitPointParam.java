package com.urbansidequest.backend.domain.param;

import com.urbansidequest.backend.domain.enums.MustVisitPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class MustVisitPointParam {

    @NotBlank
    private String name;

    private String amapPoiId;

    @Valid
    @NotNull
    private GeoPointParam location;

    @NotNull
    private MustVisitPriority priority;

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAmapPoiId() {
        return this.amapPoiId;
    }

    public void setAmapPoiId(String amapPoiId) {
        this.amapPoiId = amapPoiId;
    }

    public GeoPointParam getLocation() {
        return this.location;
    }

    public void setLocation(GeoPointParam location) {
        this.location = location;
    }

    public MustVisitPriority getPriority() {
        return this.priority;
    }

    public void setPriority(MustVisitPriority priority) {
        this.priority = priority;
    }
}
