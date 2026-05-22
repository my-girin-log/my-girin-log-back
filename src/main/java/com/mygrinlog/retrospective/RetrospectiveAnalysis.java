package com.mygrinlog.retrospective;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 카키 레퍼런스 RetrospectiveAnalysisSchema (1-pass 결과). */
public record RetrospectiveAnalysis(
        List<String> keyThemes,
        String emotionalArc,
        List<String> notableFacts,
        List<String> growthPoints,
        List<String> contradictions,
        List<String> suggestedStructure
) {
    @JsonCreator
    public RetrospectiveAnalysis(
            @JsonProperty("key_themes") List<String> keyThemes,
            @JsonProperty("emotional_arc") String emotionalArc,
            @JsonProperty("notable_facts") List<String> notableFacts,
            @JsonProperty("growth_points") List<String> growthPoints,
            @JsonProperty("contradictions") List<String> contradictions,
            @JsonProperty("suggested_structure") List<String> suggestedStructure) {
        this.keyThemes = keyThemes == null ? List.of() : keyThemes;
        this.emotionalArc = emotionalArc;
        this.notableFacts = notableFacts == null ? List.of() : notableFacts;
        this.growthPoints = growthPoints == null ? List.of() : growthPoints;
        this.contradictions = contradictions == null ? List.of() : contradictions;
        this.suggestedStructure = suggestedStructure == null ? List.of() : suggestedStructure;
    }
}
