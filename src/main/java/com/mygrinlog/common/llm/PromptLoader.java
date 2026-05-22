package com.mygrinlog.common.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 카키의 프롬프트 자산을 classpath:/prompts/ 에서 1회 로드해 캐시.
 */
@Component
public class PromptLoader {

    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

    public String load(String relativePath) {
        return cache.computeIfAbsent(relativePath, this::read);
    }

    private String read(String relativePath) {
        ClassPathResource resource = new ClassPathResource("prompts/" + relativePath);
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("프롬프트 로드 실패: " + relativePath, e);
        }
    }
}
