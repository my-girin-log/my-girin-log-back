package com.mygrinlog.pet;

import com.mygrinlog.common.time.ServiceClock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 펫 상태머신 (스펙 §4).
 *
 *  - EXP→Level: 누적, 레벨당 고정 EXP (스펙 §4.2)
 *  - Inactivity→Condition: 시간 함수, 가역, 읽을 때 재계산 (스펙 §4.3)
 *
 *  주의: condition 은 저장값이 아니다. PetState.condition_cache 컬럼은 "마지막으로 본 값" 캐시이며
 *  진실의 원천은 last_activity_at + now 다. 읽는 경로마다 재계산하고, 필요 시 캐시도 동시에 갱신.
 */
@Service
public class PetStateService {

    private final PetStateRepository petStateRepository;
    private final PetImageUrlBuilder imageUrlBuilder;
    private final PetStageCatalog stageCatalog;
    private final PetProperties properties;
    private final ServiceClock clock;

    public PetStateService(PetStateRepository petStateRepository,
                           PetImageUrlBuilder imageUrlBuilder,
                           PetStageCatalog stageCatalog,
                           PetProperties properties,
                           ServiceClock clock) {
        this.petStateRepository = petStateRepository;
        this.imageUrlBuilder = imageUrlBuilder;
        this.stageCatalog = stageCatalog;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public PetState getOrCreate(Long userId) {
        return petStateRepository.findById(userId)
                .orElseGet(() -> petStateRepository.save(new PetState(userId, clock.now())));
    }

    /** EXP 적립 + last_activity_at 동시 갱신 (스펙 §4.1). EXP 는 AI 성공과 무관하게 적립 (§5). */
    @Transactional
    public PetState gainExp(Long userId, int amount) {
        PetState state = getOrCreate(userId);
        Instant now = clock.now();
        state.addExp(amount);
        state.touch(now);
        state.setLevel(computeLevel(state.getExp()));
        state.setCondition(computeCondition(state.getLastActivityAt(), now));
        return state;
    }

    @Transactional
    public PetState gainPerMessage(Long userId) {
        return gainExp(userId, properties.exp().perMessage());
    }

    @Transactional
    public PetState gainPerDiary(Long userId) {
        return gainExp(userId, properties.exp().perDiary());
    }

    @Transactional
    public PetState gainPerRetrospective(Long userId) {
        return gainExp(userId, properties.exp().perRetrospective());
    }

    /** 펫을 프론트로 내릴 때마다 이 메서드를 통한다. condition 은 항상 lazy 재계산. (내부/레거시용) */
    @Transactional
    public PetSnapshot snapshot(Long userId) {
        PetState state = refreshAndGet(userId);
        return new PetSnapshot(
                state.getLevel(),
                state.getExp(),
                expIntoLevel(state.getExp()),
                properties.level().expPerLevel(),
                state.getCondition(),
                imageUrlBuilder.urls(state.getLevel(), state.getCondition()),
                state.getLastActivityAt()
        );
    }

    /**
     * 워넬 자료 §2.1 응답 형식. /users/me, /diaries/rollup, /retrospectives 응답에 동봉.
     * levelOverride/conditionOverride 는 모킹/데모용 — null 이면 실제 상태 사용.
     */
    @Transactional
    public PetView view(Long userId, Integer levelOverride, PetState.Condition conditionOverride) {
        PetState state = refreshAndGet(userId);
        int level = levelOverride != null ? levelOverride : state.getLevel();
        PetState.Condition condition = conditionOverride != null ? conditionOverride : state.getCondition();
        return new PetView(
                level,
                stageCatalog.stage(level),
                condition,
                state.getExp(),
                expIntoLevel(state.getExp()),
                properties.level().expPerLevel(),
                state.getLastActivityAt(),
                stageCatalog.meta(level, condition)
        );
    }

    public PetView view(Long userId) {
        return view(userId, null, null);
    }

    /** lazy 재계산 + 캐시 컬럼 동기화. snapshot/view 공통 path. */
    private PetState refreshAndGet(Long userId) {
        PetState state = getOrCreate(userId);
        Instant now = clock.now();
        PetState.Condition condition = computeCondition(state.getLastActivityAt(), now);
        if (state.getCondition() != condition) {
            state.setCondition(condition);
        }
        int level = computeLevel(state.getExp());
        if (state.getLevel() != level) {
            state.setLevel(level);
        }
        return state;
    }

    // ---- 순수 함수 (테스트하기 좋게 분리) ----

    public int computeLevel(int totalExp) {
        return Math.min(properties.level().cap(), totalExp / properties.level().expPerLevel());
    }

    public int expIntoLevel(int totalExp) {
        return totalExp % properties.level().expPerLevel();
    }

    /** 스펙 §4.3: daysInactive 는 serviceDate 기준 차이. */
    public PetState.Condition computeCondition(Instant lastActivityAt, Instant now) {
        long daysInactive = ChronoUnit.DAYS.between(
                clock.serviceDate(lastActivityAt),
                clock.serviceDate(now)
        );
        if (daysInactive <= properties.condition().goodMaxDays()) {
            return PetState.Condition.good;
        }
        if (daysInactive <= properties.condition().badMaxDays()) {
            return PetState.Condition.bad;
        }
        return PetState.Condition.terrible;
    }
}
