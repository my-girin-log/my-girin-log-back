package com.mygrinlog.persona;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 카키 레퍼런스(techgochi/src/schemas.ts PersonaSchema.analysis) 의 풍부한 어조 명세서.
 * Diary/Question/Retrospective 가 모두 이 객체를 packPersonaCore 로 패키징해서 system prompt 에 박는다.
 *
 *  - ending_dominant: 격식체 | 비격식체 | 음슴체 | 혼용
 *  - emoji_frequency: none | low | medium | high
 *  - sentence_length_avg: short | medium | long
 */
public record PersonaAnalysis(
        String tone,
        String endingDominant,
        List<String> endingStyle,
        String emojiFrequency,
        List<String> emojiExamples,
        String sentenceLengthAvg,
        List<String> signaturePhrases,
        String selfReference,
        String paragraphStyle,
        List<String> doNotUse,
        List<String> exampleSentences
) {
    @JsonCreator
    public PersonaAnalysis(
            @JsonProperty("tone") String tone,
            @JsonProperty("ending_dominant") String endingDominant,
            @JsonProperty("ending_style") List<String> endingStyle,
            @JsonProperty("emoji_frequency") String emojiFrequency,
            @JsonProperty("emoji_examples") List<String> emojiExamples,
            @JsonProperty("sentence_length_avg") String sentenceLengthAvg,
            @JsonProperty("signature_phrases") List<String> signaturePhrases,
            @JsonProperty("self_reference") String selfReference,
            @JsonProperty("paragraph_style") String paragraphStyle,
            @JsonProperty("do_not_use") List<String> doNotUse,
            @JsonProperty("example_sentences") List<String> exampleSentences) {
        this.tone = tone;
        this.endingDominant = endingDominant;
        this.endingStyle = endingStyle == null ? List.of() : endingStyle;
        this.emojiFrequency = emojiFrequency;
        this.emojiExamples = emojiExamples == null ? List.of() : emojiExamples;
        this.sentenceLengthAvg = sentenceLengthAvg;
        this.signaturePhrases = signaturePhrases == null ? List.of() : signaturePhrases;
        this.selfReference = selfReference;
        this.paragraphStyle = paragraphStyle;
        this.doNotUse = doNotUse == null ? List.of() : doNotUse;
        this.exampleSentences = exampleSentences == null ? List.of() : exampleSentences;
    }

    /** Persona 가 없거나 분석 데이터가 부족한 유저용 최소 분석 (스펙 §6 카키 합의 "fallback 프롬프트"). */
    public static PersonaAnalysis fallback() {
        return new PersonaAnalysis(
                "담담하고 간결",
                "혼용",
                List.of("~했다", "~한다"),
                "low",
                List.of(),
                "short",
                List.of(),
                "주로 '나'",
                "사실 → 감정/판단 → 다음 액션 순으로 짧게.",
                List.of("블로그식 미사여구", "과장된 감상"),
                List.of()
        );
    }
}
