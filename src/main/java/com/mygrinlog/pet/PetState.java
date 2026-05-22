package com.mygrinlog.pet;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 펫 상태 (유저당 1행).
 *
 * 주의 (스펙 §2.3 주석):
 *  - 컬럼은 condition_cache 이지만 엔티티 필드는 petCondition 으로 명명 (SQL 예약어 회피, 일부 DB 호환).
 *  - 실제 노출되는 condition 은 PetStateService 에서 last_activity_at 기반으로 lazy 재계산 (스펙 §4.3).
 *    이 컬럼은 "마지막으로 본 값" 정도의 캐시. UI 직렬화 직전 재계산해 덮어쓴 뒤 내려준다.
 */
@Entity
@Table(name = "pet_state")
public class PetState {

    public enum Condition { good, bad, terrible }

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "level", nullable = false)
    private int level;

    @Column(name = "exp", nullable = false)
    private int exp;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_cache", nullable = false, length = 16)
    private Condition petCondition;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PetState() {}

    public PetState(Long userId, Instant now) {
        this.userId = userId;
        this.level = 0;
        this.exp = 0;
        this.petCondition = Condition.good;
        this.lastActivityAt = now;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.lastActivityAt = now;
        this.updatedAt = now;
    }

    public void addExp(int amount) {
        this.exp += amount;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setCondition(Condition condition) {
        this.petCondition = condition;
    }

    public Long getUserId() { return userId; }
    public int getLevel() { return level; }
    public int getExp() { return exp; }
    public Condition getCondition() { return petCondition; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
