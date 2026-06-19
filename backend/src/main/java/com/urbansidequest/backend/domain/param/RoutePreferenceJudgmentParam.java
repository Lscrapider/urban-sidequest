package com.urbansidequest.backend.domain.param;

import com.urbansidequest.backend.domain.enums.RoutePreferenceJudgeType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RoutePreferenceJudgmentParam {

    @NotNull
    private UUID candidateSetId;

    @NotNull
    private RoutePreferenceJudgeType judgeType;

    private String judgeModel;

    private String judgePromptVersion;

    @NotEmpty
    private List<String> ranking = new ArrayList<>();

    private List<String> acceptedRouteCodes = new ArrayList<>();

    private List<String> rejectedRouteCodes = new ArrayList<>();

    private Map<String, List<String>> reasonCodes = new LinkedHashMap<>();

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal confidence;

    public UUID getCandidateSetId() {
        return this.candidateSetId;
    }

    public void setCandidateSetId(UUID candidateSetId) {
        this.candidateSetId = candidateSetId;
    }

    public RoutePreferenceJudgeType getJudgeType() {
        return this.judgeType;
    }

    public void setJudgeType(RoutePreferenceJudgeType judgeType) {
        this.judgeType = judgeType;
    }

    public String getJudgeModel() {
        return this.judgeModel;
    }

    public void setJudgeModel(String judgeModel) {
        this.judgeModel = judgeModel;
    }

    public String getJudgePromptVersion() {
        return this.judgePromptVersion;
    }

    public void setJudgePromptVersion(String judgePromptVersion) {
        this.judgePromptVersion = judgePromptVersion;
    }

    public List<String> getRanking() {
        return this.ranking;
    }

    public void setRanking(List<String> ranking) {
        this.ranking = ranking == null ? new ArrayList<>() : ranking;
    }

    public List<String> getAcceptedRouteCodes() {
        return this.acceptedRouteCodes;
    }

    public void setAcceptedRouteCodes(List<String> acceptedRouteCodes) {
        this.acceptedRouteCodes = acceptedRouteCodes == null ? new ArrayList<>() : acceptedRouteCodes;
    }

    public List<String> getRejectedRouteCodes() {
        return this.rejectedRouteCodes;
    }

    public void setRejectedRouteCodes(List<String> rejectedRouteCodes) {
        this.rejectedRouteCodes = rejectedRouteCodes == null ? new ArrayList<>() : rejectedRouteCodes;
    }

    public Map<String, List<String>> getReasonCodes() {
        return this.reasonCodes;
    }

    public void setReasonCodes(Map<String, List<String>> reasonCodes) {
        this.reasonCodes = reasonCodes == null ? new LinkedHashMap<>() : reasonCodes;
    }

    public BigDecimal getConfidence() {
        return this.confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }
}
