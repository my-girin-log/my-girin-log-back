package com.mygrinlog.chat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mygrinlog.common.llm.LlmClient;
import com.mygrinlog.common.llm.LlmProperties;
import com.mygrinlog.common.llm.PromptLoader;
import com.mygrinlog.persona.Persona;
import com.mygrinlog.persona.PersonaPacker;
import java.util.List;
import org.springframework.stereotype.Component;

/** 카키 레퍼런스 harness/question.ts 의 Spring 포트. 트랜잭션 밖 호출. */
@Component
public class ReverseQuestionGenerator {

    private static final String SYSTEM_PATH = "reverse-question-system.md";
    private static final String MODEL_KEY = "question";
    private static final int RECENT_MESSAGE_WINDOW = 8;

    private final LlmClient llm;
    private final PromptLoader prompts;
    private final PersonaPacker packer;
    private final LlmProperties properties;

    public ReverseQuestionGenerator(LlmClient llm, PromptLoader prompts,
                                    PersonaPacker packer, LlmProperties properties) {
        this.llm = llm;
        this.prompts = prompts;
        this.packer = packer;
        this.properties = properties;
    }

    public String generate(List<ChatMessage> recentMessages, Persona persona) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            throw new IllegalArgumentException("recentMessages is empty");
        }
        ChatMessage last = recentMessages.get(recentMessages.size() - 1);
        if (!llm.isEnabled()) {
            return stub(last);
        }
        String personaBlock = persona == null
                ? packer.pack("(페르소나 없음)", null)
                : packer.pack(persona);
        String userPrompt = """
                %s

                ## 최근 대화
                %s

                ## 사용자의 마지막 메시지 (이것에 꼬리 질문)
                %s""".formatted(
                personaBlock,
                formatWindow(recentMessages),
                last.getContent()
        );
        Response r = llm.call(properties.specFor(MODEL_KEY),
                prompts.load(SYSTEM_PATH), userPrompt, Response.class, MODEL_KEY);
        return r.question();
    }

    private String formatWindow(List<ChatMessage> messages) {
        int start = Math.max(0, messages.size() - RECENT_MESSAGE_WINDOW);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            sb.append('[').append(m.getRole()).append("] ").append(m.getContent()).append('\n');
        }
        return sb.toString();
    }

    private String stub(ChatMessage last) {
        String snippet = last.getContent().length() > 20
                ? last.getContent().substring(0, 20) + "..."
                : last.getContent();
        return "[stub] '" + snippet + "' — 그때 가장 답답했던 지점은 어떤 거였어?";
    }

    public record Response(String question) {
        @JsonCreator
        public Response(@JsonProperty("question") String question) {
            this.question = question == null ? "" : question.trim();
        }
    }
}
