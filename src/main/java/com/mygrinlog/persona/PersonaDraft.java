package com.mygrinlog.persona;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Persona 생성 결과 (카키 레퍼런스 PersonaSchema 와 1:1).
 *  - personaMd: 사람용 마크다운 명세서
 *  - summary: 1~2문장 요약
 *  - sources: 분석에 실제 사용된 입력 id 배열
 *  - analysis: 다른 Generator 가 packPersonaCore 로 사용할 풍부한 어조 명세
 */
public record PersonaDraft(
        String personaMd,
        String summary,
        List<String> sources,
        PersonaAnalysis analysis
) {
    @JsonCreator
    public PersonaDraft(
            @JsonProperty("persona_md") String personaMd,
            @JsonProperty("summary") String summary,
            @JsonProperty("sources") List<String> sources,
            @JsonProperty("analysis") PersonaAnalysis analysis) {
        this.personaMd = personaMd;
        this.summary = summary;
        this.sources = sources == null ? List.of() : sources;
        this.analysis = analysis == null ? PersonaAnalysis.fallback() : analysis;
    }
}
