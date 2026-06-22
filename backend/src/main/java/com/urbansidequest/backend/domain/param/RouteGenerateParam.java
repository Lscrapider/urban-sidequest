package com.urbansidequest.backend.domain.param;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.urbansidequest.backend.domain.constant.DateTimeFormatConstant;
import com.urbansidequest.backend.domain.dto.UserPreferenceProfileDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.BudgetLevel;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RouteGenerateParam {

    @NotNull
    private AreaMode areaMode;

    private String areaLabel;

    @Valid
    private GeoPointParam center;

    @Min(500)
    @Max(15000)
    private Integer radiusMeters;

    @Valid
    private List<GeoPointParam> areaPolygonGcj02 = new ArrayList<>();

    private List<String> adminAdcodes = new ArrayList<>();

    private String routeCityName;

    private String routeCityAdcode;

    @NotNull
    @JsonFormat(pattern = DateTimeFormatConstant.BEIJING_LOCAL_DATE_TIME_PATTERN)
    private LocalDateTime departureTime;

    @NotNull
    @Min(60)
    @Max(720)
    private Integer durationMinutes;

    @NotNull
    private TransportProfile transportProfile;

    @NotNull
    private RouteGoal routeGoal;

    private BudgetLevel budgetLevel = BudgetLevel.NORMAL;

    private List<String> interestTags = new ArrayList<>();

    @Valid
    private List<MustVisitPointParam> mustVisitPoints = new ArrayList<>();

    @Valid
    private UserPreferenceProfileDTO userPreferenceProfileOverride;

    public AreaMode getAreaMode() {
        return this.areaMode;
    }

    public void setAreaMode(AreaMode areaMode) {
        this.areaMode = areaMode;
    }

    public String getAreaLabel() {
        return this.areaLabel;
    }

    public void setAreaLabel(String areaLabel) {
        this.areaLabel = areaLabel;
    }

    public GeoPointParam getCenter() {
        return this.center;
    }

    public void setCenter(GeoPointParam center) {
        this.center = center;
    }

    public Integer getRadiusMeters() {
        return this.radiusMeters;
    }

    public void setRadiusMeters(Integer radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    public List<GeoPointParam> getAreaPolygonGcj02() {
        return this.areaPolygonGcj02;
    }

    public void setAreaPolygonGcj02(List<GeoPointParam> areaPolygonGcj02) {
        this.areaPolygonGcj02 = areaPolygonGcj02 == null ? new ArrayList<>() : areaPolygonGcj02;
    }

    public List<String> getAdminAdcodes() {
        return this.adminAdcodes;
    }

    public void setAdminAdcodes(List<String> adminAdcodes) {
        this.adminAdcodes = adminAdcodes == null ? new ArrayList<>() : adminAdcodes;
    }

    public String getRouteCityName() {
        return this.routeCityName;
    }

    public void setRouteCityName(String routeCityName) {
        this.routeCityName = routeCityName;
    }

    public String getRouteCityAdcode() {
        return this.routeCityAdcode;
    }

    public void setRouteCityAdcode(String routeCityAdcode) {
        this.routeCityAdcode = routeCityAdcode;
    }

    public LocalDateTime getDepartureTime() {
        return this.departureTime;
    }

    public void setDepartureTime(LocalDateTime departureTime) {
        this.departureTime = departureTime;
    }

    public Integer getDurationMinutes() {
        return this.durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public TransportProfile getTransportProfile() {
        return this.transportProfile;
    }

    public void setTransportProfile(TransportProfile transportProfile) {
        this.transportProfile = transportProfile;
    }

    public RouteGoal getRouteGoal() {
        return this.routeGoal;
    }

    public void setRouteGoal(RouteGoal routeGoal) {
        this.routeGoal = routeGoal;
    }

    public BudgetLevel getBudgetLevel() {
        return this.budgetLevel == null ? BudgetLevel.NORMAL : this.budgetLevel;
    }

    public void setBudgetLevel(BudgetLevel budgetLevel) {
        this.budgetLevel = budgetLevel == null ? BudgetLevel.NORMAL : budgetLevel;
    }

    public List<String> getInterestTags() {
        return this.interestTags;
    }

    public void setInterestTags(List<String> interestTags) {
        this.interestTags = interestTags == null ? new ArrayList<>() : interestTags;
    }

    public List<MustVisitPointParam> getMustVisitPoints() {
        return this.mustVisitPoints;
    }

    public void setMustVisitPoints(List<MustVisitPointParam> mustVisitPoints) {
        this.mustVisitPoints = mustVisitPoints == null ? new ArrayList<>() : mustVisitPoints;
    }

    public UserPreferenceProfileDTO getUserPreferenceProfileOverride() {
        return this.userPreferenceProfileOverride;
    }

    public void setUserPreferenceProfileOverride(UserPreferenceProfileDTO userPreferenceProfileOverride) {
        this.userPreferenceProfileOverride = userPreferenceProfileOverride;
    }
}
