package com.mygrinlog.retrospective;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 카키 레퍼런스 CritiqueSchema. critique 패스가 critical/major 이슈를 잡으면 revised_markdown 채워 옴. */
public record Critique(boolean passes, List<Issue> issues, String revisedMarkdown) {

    @JsonCreator
    public Critique(
            @JsonProperty("passes") boolean passes,
            @JsonProperty("issues") List<Issue> issues,
            @JsonProperty("revised_markdown") String revisedMarkdown) {
        this.passes = passes;
        this.issues = issues == null ? List.of() : issues;
        this.revisedMarkdown = revisedMarkdown;
    }

    public boolean hasCriticalOrMajor() {
        return issues.stream().anyMatch(i -> "critical".equals(i.severity()) || "major".equals(i.severity()));
    }

    public record Issue(String severity, String type, String detail) {
        @JsonCreator
        public Issue(
                @JsonProperty("severity") String severity,
                @JsonProperty("type") String type,
                @JsonProperty("detail") String detail) {
            this.severity = severity;
            this.type = type;
            this.detail = detail;
        }
    }
}
