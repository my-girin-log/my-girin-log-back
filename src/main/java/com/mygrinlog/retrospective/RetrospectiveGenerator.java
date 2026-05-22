package com.mygrinlog.retrospective;

import com.mygrinlog.common.llm.LlmClient;
import com.mygrinlog.common.llm.LlmProperties;
import com.mygrinlog.common.llm.PromptLoader;
import com.mygrinlog.diary.Diary;
import com.mygrinlog.persona.Persona;
import com.mygrinlog.persona.PersonaPacker;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 카키 레퍼런스 harness/retrospective.ts 의 3-pass 파이프라인 Spring 포트.
 *  1) Analysis (Sonnet): key_themes / emotional_arc / notable_facts / growth_points / contradictions / suggested_structure
 *  2) Writing  (Opus)  : 분석 + persona + 원본 일기 → 회고글 초안
 *  3) Critique (Sonnet, low temp): 페르소나 일관성/사실 보존/미화 여부 검토
 *
 *  critique 결과 분기:
 *   - passes=true 또는 minor only          → draft 그대로
 *   - critical/major 있음 + revised_markdown 있음 → revised_markdown 채택
 *   - critical/major 있음 + revised_markdown 없음 → fix-up prompt 로 재작성 1회
 *
 *  스펙 §5: 트랜잭션 밖 호출. sourceDiaryIds 는 호출자(RetrospectiveService) 가 주입.
 */
@Component
public class RetrospectiveGenerator {

    private static final Logger log = LoggerFactory.getLogger(RetrospectiveGenerator.class);

    private static final String ANALYSIS_PROMPT = "retrospective-analysis-system.md";
    private static final String WRITING_PROMPT = "retrospective-writing-system.md";
    private static final String CRITIQUE_PROMPT = "retrospective-critique-system.md";

    private final LlmClient llm;
    private final PromptLoader prompts;
    private final PersonaPacker packer;
    private final LlmProperties properties;

    public RetrospectiveGenerator(LlmClient llm, PromptLoader prompts,
                                  PersonaPacker packer, LlmProperties properties) {
        this.llm = llm;
        this.prompts = prompts;
        this.packer = packer;
        this.properties = properties;
    }

    public RetrospectiveDraft generate(Retrospective.Type type,
                                       LocalDate rangeStart,
                                       LocalDate rangeEnd,
                                       Map<String, Object> options,
                                       List<Diary> diaries,
                                       Persona persona) {
        if (diaries == null || diaries.isEmpty()) {
            throw new IllegalArgumentException("diaries is empty");
        }
        if (!llm.isEnabled()) {
            return stub(type, rangeStart, rangeEnd, diaries);
        }

        String personaBlock = persona == null
                ? packer.pack("(페르소나 없음)", null)
                : packer.pack(persona);
        String diarySection = formatDiaries(diaries);
        String optionsBlock = formatOptions(options);

        // ===== PASS 1: 분석 =====
        String analysisUser = """
                ## doc_type
                %s

                ## direction_options
                %s

                ## diaries
                %s""".formatted(type.name(), optionsBlock, diarySection);

        RetrospectiveAnalysis analysis = llm.call(properties.specFor("retroAnalysis"),
                prompts.load(ANALYSIS_PROMPT), analysisUser, RetrospectiveAnalysis.class, "retro:analysis");

        // ===== PASS 2: 작성 =====
        String writingUser = """
                %s

                ## doc_type
                %s

                ## direction_options
                %s

                ## 사전 분석 결과
                key_themes: %s
                emotional_arc: %s
                notable_facts: %s
                growth_points: %s
                contradictions: %s
                suggested_structure: %s

                ## 원본 일기
                %s

                ## 작업
                위 정보를 바탕으로 회고글을 작성하라. 페르소나 말투를 끝까지 유지하라.""".formatted(
                personaBlock, type.name(), optionsBlock,
                analysis.keyThemes(), analysis.emotionalArc(), analysis.notableFacts(),
                analysis.growthPoints(), analysis.contradictions(), analysis.suggestedStructure(),
                diarySection
        );

        RetrospectiveDraft draft = llm.call(properties.specFor("retroWriting"),
                prompts.load(WRITING_PROMPT), writingUser, RetrospectiveDraft.class, "retro:writing");

        // ===== PASS 3: 자기 검토 =====
        String critiqueUser = """
                %s

                ## 작성된 회고글
                title: %s
                markdown:
                %s

                ## 원본 일기 (사실 검증용)
                %s

                ## 검토 요청
                위 회고글이 다음을 만족하는지 검토하라:
                - 페르소나 말투 일관성
                - 일기 사실 보존 (창작 없음)
                - 미화/과장 없음
                - doc_type=%s 및 옵션 준수""".formatted(
                personaBlock, draft.title(), draft.markdown(), diarySection, type.name()
        );

        Critique critique = llm.call(properties.specFor("critique"),
                prompts.load(CRITIQUE_PROMPT), critiqueUser, Critique.class, "retro:critique");

        if (critique.passes() && !critique.hasCriticalOrMajor()) {
            log.debug("[retro] critique passed");
            return draft;
        }

        // critique 가 수정본을 줬으면 사용
        if (critique.revisedMarkdown() != null && !critique.revisedMarkdown().isBlank()) {
            log.info("[retro] using critique's revised_markdown ({} issues)", critique.issues().size());
            return draft.withMarkdown(critique.revisedMarkdown());
        }

        // 수정본을 안 줬으면 fix-up prompt 로 재작성 1회
        log.info("[retro] critique flagged {} issues but no revision; regenerating", critique.issues().size());
        String fixupUser = """
                %s

                ## 원본 일기 (이것만 사용. 이 외 사실 추가 금지)
                %s

                ## 이전 시도에서 발견된 문제
                %s

                ## 이전 작성본
                %s

                ## 작업
                위 문제들을 모두 수정한 회고글을 다시 작성하라.
                일기에 없는 사실/디테일/해석은 절대 추가하지 마라.
                일기 내용이 짧으면 회고글도 짧게. 부풀리지 마라.""".formatted(
                personaBlock, diarySection,
                formatIssues(critique),
                draft.markdown()
        );

        RetrospectiveDraft refined = llm.call(properties.specFor("retroWriting"),
                prompts.load(WRITING_PROMPT), fixupUser, RetrospectiveDraft.class, "retro:refine");
        return draft.withTitleAndMarkdown(refined.title(), refined.markdown());
    }

    private String formatDiaries(List<Diary> diaries) {
        StringBuilder sb = new StringBuilder();
        for (Diary d : diaries) {
            sb.append("### [diary_").append(d.getId()).append("] ")
              .append(d.getDateKey()).append(" - ").append(d.getTitle()).append('\n')
              .append(d.getMarkdown()).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    private String formatOptions(Map<String, Object> options) {
        if (options == null || options.isEmpty()) {
            return "focus: (없음)\ntone: (페르소나 따름)\nlength: medium";
        }
        return "focus: %s\ntone: %s\nlength: %s".formatted(
                options.getOrDefault("focus", "(없음)"),
                options.getOrDefault("tone", "(페르소나 따름)"),
                options.getOrDefault("length", "medium")
        );
    }

    private String formatIssues(Critique critique) {
        StringBuilder sb = new StringBuilder();
        for (Critique.Issue i : critique.issues()) {
            if ("minor".equals(i.severity())) continue;
            sb.append("- [").append(i.severity()).append("] ")
              .append(i.type()).append(": ").append(i.detail()).append('\n');
        }
        return sb.toString();
    }

    private RetrospectiveDraft stub(Retrospective.Type type, LocalDate from, LocalDate to, List<Diary> diaries) {
        String title = "[stub] %s 회고 (%s ~ %s)".formatted(type, from, to);
        String markdown = "# " + title + "\n\nLLM_ENABLED=false 스텁 응답입니다.\n다이어리 " + diaries.size() + "건을 모았습니다.\n";
        String summary = "스텁 회고 " + diaries.size() + "건 기반.";
        return new RetrospectiveDraft(title, markdown, summary, List.of("stub", type.name()));
    }
}
