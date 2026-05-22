package com.mygrinlog.pet;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 스펙 §2.4: 펫 이미지 36장은 컨벤션 URL 로 끝낸다. DB 테이블 없음.
 * imageUrl(level, condition, frame) = {CDN_BASE}/lvl{level}_{condition}_{frame}.png
 */
@Component
public class PetImageUrlBuilder {

    private final PetProperties properties;

    public PetImageUrlBuilder(PetProperties properties) {
        this.properties = properties;
    }

    public String url(int level, PetState.Condition condition, int frame) {
        return "%s/lvl%d_%s_%d.png".formatted(properties.petImage().cdnBase(), level, condition.name(), frame);
    }

    public List<String> urls(int level, PetState.Condition condition) {
        int frames = properties.petImage().frames();
        List<String> result = new ArrayList<>(frames);
        for (int i = 1; i <= frames; i++) {
            result.add(url(level, condition, i));
        }
        return result;
    }
}
