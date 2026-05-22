package com.mygrinlog.common.llm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Spring AI Anthropic ChatClient 얇은 래퍼. 카키 레퍼런스(techgochi/src/utils.ts) 패턴 차용:
 *  - system prompt 뒤에 &lt;output&gt;...&lt;/output&gt; 강제 규칙 자동 추가
 *  - JSON 추출 3단 fallback (JsonExtractor)
 *  - 호출 실패/파싱 실패 시 재시도 (총 시도 = wootegotchi.llm.retries + 1)
 *  - per-call 모델/온도/maxTokens 지정 (생성기마다 다른 모델 — Persona=Opus, Question=Haiku, ...)
 *
 *  스펙 §5: 이 메서드는 어떤 트랜잭션 밖에서도 호출되도록 설계. 자체적으로 @Transactional 안 검.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    /** 모든 system 뒤에 자동으로 붙는 규칙 — JSON 만 받기 위해. */
    private static final String OUTPUT_RULE = """


            # CRITICAL OUTPUT RULE
            응답은 반드시 다음 형식으로만 출력하라:
            <output>
            {여기에 JSON}
            </output>
            <output> 태그 밖에는 어떤 텍스트도 출력하지 마라. JSON 외 자연어/코드펜스/주석 금지.
            """;

    /** 일부 최신 모델은 temperature 미지원 — 카키 레퍼런스의 TEMP_UNSUPPORTED 매칭. */
    private static final String[] TEMPERATURE_UNSUPPORTED_PREFIXES = { "claude-opus-4-7" };

    private final ChatClient chatClient;
    private final boolean enabled;
    private final int retries;
    private final ObjectMapper mapper;

    public LlmClient(ObjectProvider<ChatModel> chatModelProvider, LlmProperties properties) {
        ChatModel model = chatModelProvider.getIfAvailable();
        this.enabled = properties.enabled() && model != null;
        this.chatClient = enabled ? ChatClient.builder(model).build() : null;
        this.retries = Math.max(0, properties.retries());
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 카키의 callLLMWithSchema 와 동일한 책임:
     *  Claude 호출 → text 추출 → JSON 추출 → Jackson 파싱 → (실패 시 재시도)
     */
    public <T> T call(LlmProperties.ModelSpec spec, String systemPrompt, String userPrompt, Class<T> type, String label) {
        if (!enabled) {
            throw new IllegalStateException("LlmClient is disabled — caller must branch on isEnabled() first.");
        }
        String enforcedSystem = systemPrompt + OUTPUT_RULE;
        AnthropicChatOptions options = buildOptions(spec);

        Exception lastError = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                String body = chatClient.prompt()
                        .system(enforcedSystem)
                        .user(userPrompt)
                        .options(options)
                        .call()
                        .content();
                String json = JsonExtractor.extract(body);
                return mapper.readValue(json, type);
            } catch (Exception e) {
                lastError = e;
                log.warn("[{}] LLM call attempt {} failed: {}", label, attempt + 1, e.toString());
            }
        }
        throw new IllegalStateException(
                "[" + label + "] LLM call failed after " + (retries + 1) + " attempts", lastError);
    }

    private AnthropicChatOptions buildOptions(LlmProperties.ModelSpec spec) {
        AnthropicChatOptions.Builder b = AnthropicChatOptions.builder()
                .withModel(spec.model())
                .withMaxTokens(spec.maxTokens());
        if (supportsTemperature(spec.model())) {
            b.withTemperature(spec.temperature());
        }
        return b.build();
    }

    private static boolean supportsTemperature(String model) {
        for (String unsupported : TEMPERATURE_UNSUPPORTED_PREFIXES) {
            if (model != null && model.contains(unsupported)) return false;
        }
        return true;
    }
}
