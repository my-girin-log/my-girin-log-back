package com.mygrinlog.common.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Claude 응답에서 JSON 본문 추출. 카키 레퍼런스(techgochi/src/utils.ts extractJson) 와 동일한 3단 fallback.
 *  1. &lt;output&gt;...&lt;/output&gt; 태그
 *  2. ```json ... ``` 코드 펜스
 *  3. 첫 '{' ~ 마지막 '}'
 */
final class JsonExtractor {

    private static final Pattern OUTPUT_TAG =
            Pattern.compile("<output>([\\s\\S]*?)</output>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_FENCE =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private JsonExtractor() {}

    static String extract(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("LLM returned empty response");
        }
        Matcher tag = OUTPUT_TAG.matcher(raw);
        if (tag.find()) return tag.group(1).trim();

        Matcher fence = CODE_FENCE.matcher(raw);
        if (fence.find()) return fence.group(1).trim();

        int first = raw.indexOf('{');
        int last = raw.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return raw.substring(first, last + 1);
        }
        throw new IllegalStateException("Cannot extract JSON from LLM response: " +
                raw.substring(0, Math.min(raw.length(), 200)));
    }
}
