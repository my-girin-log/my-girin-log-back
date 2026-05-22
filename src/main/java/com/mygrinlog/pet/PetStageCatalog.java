package com.mygrinlog.pet;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 워넬 자료의 PET_META_MAP 그대로 — level×condition → stage/stateNumber/spriteRowIndex.
 *
 *  - stage: calf(레벨 0) | adolescent(레벨 1) | adult(레벨 2)
 *  - stateNumber: 1~9 (워넬 자료 §3 매핑 표)
 *  - stateKey: "{stateNumber}-{stage}-{condition}", 프론트 로컬 자산 경로 빌드에 사용
 *  - spriteRowIndex: stateNumber - 1 (스프라이트 시트 row)
 *
 * 프론트 연동 두 가지 방법 모두 이 한 객체로 커버:
 *  A) 개별 파일:   `/assets/giraffe/${stateKey}-${frame}.png`
 *  B) 스프라이트:  CSS background-position 으로 spriteRowIndex × FRAME_HEIGHT 변위
 */
@Component
public class PetStageCatalog {

    public static final int TOTAL_FRAMES = 4;

    public record Meta(int stateNumber, String stateKey, int totalFrames, int spriteRowIndex) {}

    private record Key(int level, PetState.Condition condition) {}

    private static final Map<Integer, String> STAGE_BY_LEVEL = Map.of(
            0, "calf",
            1, "adolescent",
            2, "adult"
    );

    private static final Map<Key, Meta> META = Map.ofEntries(
            Map.entry(new Key(0, PetState.Condition.good),     new Meta(1, "1-calf-good",          TOTAL_FRAMES, 0)),
            Map.entry(new Key(0, PetState.Condition.bad),      new Meta(2, "2-calf-bad",           TOTAL_FRAMES, 1)),
            Map.entry(new Key(0, PetState.Condition.terrible), new Meta(3, "3-calf-terrible",      TOTAL_FRAMES, 2)),
            Map.entry(new Key(1, PetState.Condition.good),     new Meta(4, "4-adolescent-good",    TOTAL_FRAMES, 3)),
            Map.entry(new Key(1, PetState.Condition.bad),      new Meta(5, "5-adolescent-bad",     TOTAL_FRAMES, 4)),
            Map.entry(new Key(1, PetState.Condition.terrible), new Meta(6, "6-adolescent-terrible",TOTAL_FRAMES, 5)),
            Map.entry(new Key(2, PetState.Condition.good),     new Meta(7, "7-adult-good",         TOTAL_FRAMES, 6)),
            Map.entry(new Key(2, PetState.Condition.bad),      new Meta(8, "8-adult-bad",          TOTAL_FRAMES, 7)),
            Map.entry(new Key(2, PetState.Condition.terrible), new Meta(9, "9-adult-terrible",     TOTAL_FRAMES, 8))
    );

    public String stage(int level) {
        return STAGE_BY_LEVEL.getOrDefault(level, "adult");
    }

    public Meta meta(int level, PetState.Condition condition) {
        Meta m = META.get(new Key(clampLevel(level), condition));
        if (m == null) {
            throw new IllegalStateException("Missing pet meta for level=" + level + " condition=" + condition);
        }
        return m;
    }

    private int clampLevel(int level) {
        if (level < 0) return 0;
        if (level > 2) return 2;
        return level;
    }
}
