package com.mygrinlog.retrospective;

import com.mygrinlog.common.jpa.BaseTimeEntity;
import com.mygrinlog.common.jpa.JsonLongListConverter;
import com.mygrinlog.common.jpa.JsonMapConverter;
import com.mygrinlog.common.jpa.JsonStringListConverter;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "retrospective", indexes = @Index(name = "idx_retro_user_created", columnList = "user_id, created_at"))
public class Retrospective extends BaseTimeEntity {

    public enum Type { tech_blog, emotion, woowacourse, freeform }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "markdown", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String markdown;

    @Column(name = "summary", length = 500)
    private String summary;

    @Convert(converter = JsonStringListConverter.class)
    @Lob
    @Column(name = "tags")
    private List<String> tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private Type type;

    @Convert(converter = JsonMapConverter.class)
    @Lob
    @Column(name = "prompt_options")
    private Map<String, Object> promptOptions;

    @Column(name = "range_start_date", nullable = false)
    private LocalDate rangeStartDate;

    @Column(name = "range_end_date", nullable = false)
    private LocalDate rangeEndDate;

    /** AI 가 지어내지 못하게 백엔드가 주입한다 (스펙 §5). FK 아님 — 다이어리가 수정/삭제돼도 회고는 보존. */
    @Convert(converter = JsonLongListConverter.class)
    @Lob
    @Column(name = "source_diary_ids")
    private List<Long> sourceDiaryIds;

    protected Retrospective() {}

    public Retrospective(Long userId, String title, String markdown, String summary,
                         List<String> tags, Type type, Map<String, Object> promptOptions,
                         LocalDate rangeStartDate, LocalDate rangeEndDate, List<Long> sourceDiaryIds) {
        this.userId = userId;
        this.title = title;
        this.markdown = markdown;
        this.summary = summary;
        this.tags = tags;
        this.type = type;
        this.promptOptions = promptOptions;
        this.rangeStartDate = rangeStartDate;
        this.rangeEndDate = rangeEndDate;
        this.sourceDiaryIds = sourceDiaryIds;
    }

    public void update(String title, String markdown, String summary, List<String> tags) {
        this.title = title;
        this.markdown = markdown;
        this.summary = summary;
        this.tags = tags;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getMarkdown() { return markdown; }
    public String getSummary() { return summary; }
    public List<String> getTags() { return tags; }
    public Type getType() { return type; }
    public Map<String, Object> getPromptOptions() { return promptOptions; }
    public LocalDate getRangeStartDate() { return rangeStartDate; }
    public LocalDate getRangeEndDate() { return rangeEndDate; }
    public List<Long> getSourceDiaryIds() { return sourceDiaryIds; }
}
