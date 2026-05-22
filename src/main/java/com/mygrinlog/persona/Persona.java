package com.mygrinlog.persona;

import com.mygrinlog.common.jpa.BaseTimeEntity;
import com.mygrinlog.common.jpa.JsonStringListConverter;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "persona", uniqueConstraints = @UniqueConstraint(name = "uk_persona_user", columnNames = "user_id"))
public class Persona extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "markdown", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String markdown;

    @Column(name = "summary", length = 500)
    private String summary;

    @Convert(converter = JsonStringListConverter.class)
    @Lob
    @Column(name = "sources")
    private List<String> sources;

    /** 카키 레퍼런스 의 풍부한 analysis. Diary/Question/Retro 가 packPersonaCore 로 사용. */
    @Convert(converter = PersonaAnalysisConverter.class)
    @Lob
    @Column(name = "analysis")
    private PersonaAnalysis analysis;

    protected Persona() {}

    public Persona(Long userId, String markdown, String summary, List<String> sources, PersonaAnalysis analysis) {
        this.userId = userId;
        this.markdown = markdown;
        this.summary = summary;
        this.sources = sources;
        this.analysis = analysis;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getMarkdown() { return markdown; }
    public String getSummary() { return summary; }
    public List<String> getSources() { return sources; }
    public PersonaAnalysis getAnalysis() { return analysis; }

    public void update(String markdown, String summary, List<String> sources, PersonaAnalysis analysis) {
        this.markdown = markdown;
        this.summary = summary;
        this.sources = sources;
        this.analysis = analysis;
    }
}
