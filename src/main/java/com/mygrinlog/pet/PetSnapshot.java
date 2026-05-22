package com.mygrinlog.pet;

import java.time.Instant;
import java.util.List;

/**
 * 프론트로 내려갈 펫 상태. 스펙 §4.4.
 * - condition 은 항상 lazy 재계산된 값.
 * - expIntoLevel/levelUpExp 는 Frame 6 게이지 용 신규 필드.
 */
public record PetSnapshot(
        int level,
        int exp,
        int expIntoLevel,
        int levelUpExp,
        PetState.Condition condition,
        List<String> imageUrls,
        Instant lastActivityAt
) {}
