package com.mygrinlog.common.llm;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카키 레퍼런스(techgochi) 의 MODEL_CONFIG 를 application.yml 로 빼낸 구조.
 * 모델/온도/토큰을 코드 수정 없이 튜닝.
 */
@ConfigurationProperties(prefix = "wootegotchi.llm")
public record LlmProperties(boolean enabled, int retries, Map<String, ModelSpec> models) {

    public record ModelSpec(String model, double temperature, int maxTokens) {}

    /** application.yml 의 키 (persona, question, diary, retroAnalysis, retroWriting, critique). */
    public ModelSpec specFor(String generatorKey) {
        ModelSpec spec = models == null ? null : models.get(generatorKey);
        if (spec == null) {
            throw new IllegalStateException(
                    "LLM model spec missing for key '" + generatorKey + "'. Check wootegotchi.llm.models.* in application.yml");
        }
        return spec;
    }
}
