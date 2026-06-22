package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@TableName("poi_semantic_mapping")
public class PoiSemanticMappingPO {

    @TableId("id")
    private UUID id;

    @TableField("mapping_code")
    private String mappingCode;

    @TableField("display_name")
    private String displayName;

    @TableField(value = "amap_type_prefixes", select = false)
    private List<String> amapTypePrefixes;

    @TableField(value = "exact_typecodes", select = false)
    private List<String> exactTypecodes;

    @TableField(value = "keyword_patterns", select = false)
    private List<String> keywordPatterns;

    @TableField("category_group")
    private String categoryGroup;

    @TableField("primary_category_group")
    private String primaryCategoryGroup;

    @TableField(value = "interest_tag_codes", select = false)
    private List<String> interestTagCodes;

    @TableField("is_classic")
    private Boolean classic;

    @TableField("is_local")
    private Boolean local;

    @TableField("is_photo_friendly")
    private Boolean photoFriendly;

    @TableField("is_night_friendly")
    private Boolean nightFriendly;

    @TableField("is_quiet")
    private Boolean quiet;

    @TableField("is_hidden_gem")
    private Boolean hiddenGem;

    @TableField("meal_candidate")
    private Boolean mealCandidate;

    @TableField("rest_candidate")
    private Boolean restCandidate;

    @TableField("local_experience_candidate")
    private Boolean localExperienceCandidate;

    @TableField("weather_sensitivity")
    private BigDecimal weatherSensitivity;

    @TableField("mapping_version")
    private String mappingVersion;

    private Integer priority;

    private Boolean enabled;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public UUID getId() {
        return this.id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getMappingCode() {
        return this.mappingCode;
    }

    public void setMappingCode(String mappingCode) {
        this.mappingCode = mappingCode;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<String> getAmapTypePrefixes() {
        return this.amapTypePrefixes;
    }

    public void setAmapTypePrefixes(List<String> amapTypePrefixes) {
        this.amapTypePrefixes = amapTypePrefixes;
    }

    public List<String> getExactTypecodes() {
        return this.exactTypecodes;
    }

    public void setExactTypecodes(List<String> exactTypecodes) {
        this.exactTypecodes = exactTypecodes;
    }

    public List<String> getKeywordPatterns() {
        return this.keywordPatterns;
    }

    public void setKeywordPatterns(List<String> keywordPatterns) {
        this.keywordPatterns = keywordPatterns;
    }

    public String getCategoryGroup() {
        return this.categoryGroup;
    }

    public void setCategoryGroup(String categoryGroup) {
        this.categoryGroup = categoryGroup;
    }

    public String getPrimaryCategoryGroup() {
        return this.primaryCategoryGroup;
    }

    public void setPrimaryCategoryGroup(String primaryCategoryGroup) {
        this.primaryCategoryGroup = primaryCategoryGroup;
    }

    public List<String> getInterestTagCodes() {
        return this.interestTagCodes;
    }

    public void setInterestTagCodes(List<String> interestTagCodes) {
        this.interestTagCodes = interestTagCodes;
    }

    public Boolean getClassic() {
        return this.classic;
    }

    public void setClassic(Boolean classic) {
        this.classic = classic;
    }

    public Boolean getLocal() {
        return this.local;
    }

    public void setLocal(Boolean local) {
        this.local = local;
    }

    public Boolean getPhotoFriendly() {
        return this.photoFriendly;
    }

    public void setPhotoFriendly(Boolean photoFriendly) {
        this.photoFriendly = photoFriendly;
    }

    public Boolean getNightFriendly() {
        return this.nightFriendly;
    }

    public void setNightFriendly(Boolean nightFriendly) {
        this.nightFriendly = nightFriendly;
    }

    public Boolean getQuiet() {
        return this.quiet;
    }

    public void setQuiet(Boolean quiet) {
        this.quiet = quiet;
    }

    public Boolean getHiddenGem() {
        return this.hiddenGem;
    }

    public void setHiddenGem(Boolean hiddenGem) {
        this.hiddenGem = hiddenGem;
    }

    public Boolean getMealCandidate() {
        return this.mealCandidate;
    }

    public void setMealCandidate(Boolean mealCandidate) {
        this.mealCandidate = mealCandidate;
    }

    public Boolean getRestCandidate() {
        return this.restCandidate;
    }

    public void setRestCandidate(Boolean restCandidate) {
        this.restCandidate = restCandidate;
    }

    public Boolean getLocalExperienceCandidate() {
        return this.localExperienceCandidate;
    }

    public void setLocalExperienceCandidate(Boolean localExperienceCandidate) {
        this.localExperienceCandidate = localExperienceCandidate;
    }

    public BigDecimal getWeatherSensitivity() {
        return this.weatherSensitivity;
    }

    public void setWeatherSensitivity(BigDecimal weatherSensitivity) {
        this.weatherSensitivity = weatherSensitivity;
    }

    public String getMappingVersion() {
        return this.mappingVersion;
    }

    public void setMappingVersion(String mappingVersion) {
        this.mappingVersion = mappingVersion;
    }

    public Integer getPriority() {
        return this.priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
