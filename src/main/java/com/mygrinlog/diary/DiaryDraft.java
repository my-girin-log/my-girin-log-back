package com.mygrinlog.diary;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 카키 레퍼런스 DiarySchema 와 1:1. */
public record DiaryDraft(String title, String rawText, String markdown, String emotionEmoji, List<String> tags) {

    @JsonCreator
    public DiaryDraft(
            @JsonProperty("title") String title,
            @JsonProperty("rawText") String rawText,
            @JsonProperty("markdown") String markdown,
            @JsonProperty("emotionEmoji") String emotionEmoji,
            @JsonProperty("tags") List<String> tags) {
        this.title = title;
        this.rawText = rawText;
        this.markdown = markdown;
        this.emotionEmoji = emotionEmoji;
        this.tags = tags == null ? List.of() : tags;
    }
}
