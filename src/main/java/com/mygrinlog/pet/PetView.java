package com.mygrinlog.pet;

import java.time.Instant;

/**
 * 프론트로 내려가는 펫 응답 (워넬 자료 §2.1 /users/me pet 블록).
 *
 *  - condition: enum 원값(good/bad/terrible). 라벨 변환은 프론트(예: Great).
 *  - expIntoLevel/levelUpExp: 스펙 §6 워넬 합의로 추가된 게이지용 필드.
 *  - meta: 워넬 §3 PET_META_MAP — 프론트 로컬 자산 매핑용.
 */
public record PetView(
        int level,
        String stage,
        PetState.Condition condition,
        int exp,
        int expIntoLevel,
        int levelUpExp,
        Instant lastActivityAt,
        PetStageCatalog.Meta meta
) {}
