package com.mygrinlog.diary;

import com.mygrinlog.common.jpa.BaseTimeEntity;
import com.mygrinlog.common.jpa.JsonStringListConverter;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(
        name = "diary",
        uniqueConstraints = @UniqueConstraint(name = "uk_diary_user_date", columnNames = {"user_id", "date_key"})
)
public class Diary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "date_key", nullable = false)
    private LocalDate dateKey;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "raw_text", columnDefinition = "MEDIUMTEXT")
    private String rawText;

    @Column(name = "markdown", nullable = false, columnDefinition = "TEXT")
    private String markdown;

    @Column(name = "emotion_emoji", length = 16)
    private String emotionEmoji;

    @Convert(converter = JsonStringListConverter.class)
    @Lob
    @Column(name = "tags")
    private List<String> tags;

    protected Diary() {}

    public Diary(Long userId, LocalDate dateKey, String title, String rawText, String markdown,
                 String emotionEmoji, List<String> tags) {
        this.userId = userId;
        this.dateKey = dateKey;
        this.title = title;
        this.rawText = rawText;
        this.markdown = markdown;
        this.emotionEmoji = emotionEmoji;
        this.tags = tags;
    }

    public void update(String title, String markdown, String emotionEmoji, List<String> tags) {
        this.title = title;
        this.markdown = markdown;
        this.emotionEmoji = emotionEmoji;
        this.tags = tags;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getDateKey() { return dateKey; }
    public String getTitle() { return title; }
    public String getRawText() { return rawText; }
    public String getMarkdown() { return markdown; }
    public String getEmotionEmoji() { return emotionEmoji; }
    public List<String> getTags() { return tags; }
}
