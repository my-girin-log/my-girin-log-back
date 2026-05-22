package com.mygrinlog.persona;

import com.mygrinlog.common.llm.LlmClient;
import com.mygrinlog.common.llm.LlmProperties;
import com.mygrinlog.common.llm.PromptLoader;
import java.util.List;
import org.springframework.stereotype.Component;

/** 카키 레퍼런스 harness/persona.ts 의 Spring 포트. 트랜잭션 밖 호출. */
@Component
public class PersonaGenerator {

    private static final String SYSTEM_PATH = "persona-system.md";
    private static final String MODEL_KEY = "persona";

    private final LlmClient llm;
    private final PromptLoader prompts;
    private final LlmProperties properties;

    public PersonaGenerator(LlmClient llm, PromptLoader prompts, LlmProperties properties) {
        this.llm = llm;
        this.prompts = prompts;
        this.properties = properties;
    }

    public PersonaDraft generate(List<String> sourceUrls, String rawText) {
        if ((sourceUrls == null || sourceUrls.isEmpty()) && (rawText == null || rawText.isBlank())) {
            throw new IllegalArgumentException("At least one source URL or rawText required");
        }
        if (!llm.isEnabled()) {
            return stub(sourceUrls, rawText);
        }
        String userPrompt = buildUserPrompt(sourceUrls, rawText);
        return llm.call(properties.specFor(MODEL_KEY),
                prompts.load(SYSTEM_PATH), userPrompt, PersonaDraft.class, MODEL_KEY);
    }

    private String buildUserPrompt(List<String> sourceUrls, String rawText) {
        StringBuilder sb = new StringBuilder();
        if (sourceUrls != null && !sourceUrls.isEmpty()) {
            sb.append("## blog_links\n");
            for (int i = 0; i < sourceUrls.size(); i++) {
                sb.append("### [link_").append(i + 1).append("]\n").append(sourceUrls.get(i)).append("\n\n");
            }
        }
        if (rawText != null && !rawText.isBlank()) {
            sb.append("## past_writings\n### [text_1]\n").append(rawText).append('\n');
        }
        return sb.toString();
    }

    private PersonaDraft stub(List<String> sourceUrls, String rawText) {
        String md = prompts.load("persona-fallback.md");
        int textLen = rawText == null ? 0 : rawText.length();
        int linkCount = sourceUrls == null ? 0 : sourceUrls.size();
        String summary = "샘플 페르소나 (LLM_ENABLED=false). 입력 길이 " + textLen + "자, 링크 " + linkCount + "개.";
        return new PersonaDraft(md, summary,
                sourceUrls == null ? List.of() : sourceUrls,
                PersonaAnalysis.fallback());
    }
}
