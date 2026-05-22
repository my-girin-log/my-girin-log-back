package com.mygrinlog.diary;

import com.mygrinlog.chat.ChatMessage;
import com.mygrinlog.common.llm.LlmClient;
import com.mygrinlog.common.llm.LlmProperties;
import com.mygrinlog.common.llm.PromptLoader;
import com.mygrinlog.persona.Persona;
import com.mygrinlog.persona.PersonaPacker;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/** 카키 레퍼런스 harness/diary.ts 의 Spring 포트. 트랜잭션 밖 호출 (스펙 §5). */
@Component
public class DiaryGenerator {

    private static final String SYSTEM_PATH = "diary-system.md";
    private static final String MODEL_KEY = "diary";

    private final LlmClient llm;
    private final PromptLoader prompts;
    private final PersonaPacker packer;
    private final LlmProperties properties;

    public DiaryGenerator(LlmClient llm, PromptLoader prompts,
                          PersonaPacker packer, LlmProperties properties) {
        this.llm = llm;
        this.prompts = prompts;
        this.packer = packer;
        this.properties = properties;
    }

    public DiaryDraft generate(LocalDate dateKey, List<ChatMessage> messages, Persona persona) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages is empty");
        }
        if (!llm.isEnabled()) {
            return stub(dateKey, messages);
        }
        String personaBlock = persona == null
                ? packer.pack("(페르소나 없음)", null)
                : packer.pack(persona);
        String userPrompt = """
                %s

                ## 어제(%s)의 raw chat log
                %s

                ## 작업
                위 chat log 를 페르소나 말투로 일기 형태로 정리하라.
                원칙: 사용자가 한 말만 보존, 미화/창작 금지. AI(assistant) 발화는 컨텍스트로만 사용.""".formatted(
                personaBlock, dateKey, formatMessages(messages)
        );
        return llm.call(properties.specFor(MODEL_KEY),
                prompts.load(SYSTEM_PATH), userPrompt, DiaryDraft.class, MODEL_KEY);
    }

    private String formatMessages(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            sb.append('[').append(m.getRole()).append("] ").append(m.getContent()).append('\n');
        }
        return sb.toString();
    }

    private DiaryDraft stub(LocalDate dateKey, List<ChatMessage> messages) {
        StringBuilder body = new StringBuilder();
        body.append("# ").append(dateKey).append(" 다이어리\n\n");
        body.append("### 1. 무슨 일이 있었나요?\n");
        long userCount = messages.stream().filter(m -> m.getRole() == ChatMessage.Role.user).count();
        body.append("- 사용자 메시지 ").append(userCount).append("건 기록됨 (LLM_ENABLED=false 스텁).\n\n");
        body.append("### 2. 감정/생각\n- 기록 없음\n\n");
        body.append("### 3. 배운 점 또는 다음 액션\n- 기록 없음\n");
        StringBuilder rawText = new StringBuilder();
        messages.stream()
                .filter(m -> m.getRole() == ChatMessage.Role.user)
                .forEach(m -> rawText.append(m.getContent()).append(' '));
        return new DiaryDraft(
                dateKey + " 기록",
                rawText.toString().trim(),
                body.toString(),
                "📝",
                List.of("stub")
        );
    }
}
