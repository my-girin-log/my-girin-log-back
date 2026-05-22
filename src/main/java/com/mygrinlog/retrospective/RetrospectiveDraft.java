package com.mygrinlog.retrospective;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 카키 레퍼런스 RetrospectiveSchema. 단, sourceDiaryIds 는 AI 출력을 무시하고 백엔드가 주입 (스펙 §5).
 * 여기서는 AI 응답을 받기 위한 그릇으로만 두고, 최종 저장 시점에 백엔드가 덮어쓴다.
 */
public record RetrospectiveDraft(String title, String markdown, String summary, List<String> tags) {

    @JsonCreator
    public RetrospectiveDraft(
            @JsonProperty("title") String title,
            @JsonProperty("markdown") String markdown,
            @JsonProperty("summary") String summary,
            @JsonProperty("tags") List<String> tags) {
        this.title = title;
        this.markdown = markdown;
        this.summary = summary;
        this.tags = tags == null ? List.of() : tags;
    }

    public RetrospectiveDraft withMarkdown(String newMarkdown) {
        return new RetrospectiveDraft(this.title, newMarkdown, this.summary, this.tags);
    }

    public RetrospectiveDraft withTitleAndMarkdown(String newTitle, String newMarkdown) {
        return new RetrospectiveDraft(newTitle, newMarkdown, this.summary, this.tags);
    }
}
