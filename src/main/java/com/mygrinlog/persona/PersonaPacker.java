package com.mygrinlog.persona;

import org.springframework.stereotype.Component;

/**
 * 카키 레퍼런스(techgochi/src/utils.ts packPersonaCore) 와 동일 책임.
 *
 * Persona 의 풍부한 analysis 를 다른 Generator(Question/Diary/Retrospective) 에
 * "짧고 강력한" 한 블록으로 패키징해 system 또는 user prompt 에 박는다.
 *
 * 페르소나 markdown 전체를 그대로 박으면 토큰 낭비 + 모델이 핵심을 놓치므로
 * 어조 재현에 결정적인 필드만 추려서 보낸다.
 */
@Component
public class PersonaPacker {

    public String pack(String summary, PersonaAnalysis analysis) {
        if (analysis == null) {
            analysis = PersonaAnalysis.fallback();
        }
        String doNotUse = (analysis.doNotUse() == null || analysis.doNotUse().isEmpty())
                ? ""
                : "- 절대 쓰지 말 것: " + String.join(", ", analysis.doNotUse()) + "\n";

        String examples = analysis.exampleSentences().isEmpty()
                ? "(관찰된 예시 없음)"
                : numbered(analysis.exampleSentences());

        String emojiExamples = analysis.emojiExamples().isEmpty()
                ? ""
                : " (" + String.join(" ", limit(analysis.emojiExamples(), 5)) + ")";

        return """
                [USER PERSONA - 반드시 이 말투를 그대로 재현]
                - 요약: %s
                - 어조: %s
                - 우세 종결: %s
                - 자주 쓰는 종결어미: %s
                - 자기지칭: %s
                - 시그니처 표현: %s
                - 이모지 사용: %s%s
                - 평균 문장 길이: %s
                %s

                [원문 예시 - 이 문체를 그대로 모방]
                %s""".formatted(
                nullSafe(summary),
                nullSafe(analysis.tone()),
                nullSafe(analysis.endingDominant()),
                String.join(", ", analysis.endingStyle()),
                nullSafe(analysis.selfReference()),
                String.join(", ", analysis.signaturePhrases()),
                nullSafe(analysis.emojiFrequency()),
                emojiExamples,
                nullSafe(analysis.sentenceLengthAvg()),
                doNotUse,
                examples
        );
    }

    public String pack(Persona persona) {
        return pack(persona.getSummary(), persona.getAnalysis());
    }

    private static String nullSafe(String s) {
        return s == null ? "(없음)" : s;
    }

    private static java.util.List<String> limit(java.util.List<String> src, int n) {
        return src.size() <= n ? src : src.subList(0, n);
    }

    private static String numbered(java.util.List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            sb.append(i + 1).append(". ").append(items.get(i));
            if (i < items.size() - 1) sb.append('\n');
        }
        return sb.toString();
    }
}
