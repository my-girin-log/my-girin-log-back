package com.mygrinlog.pet;

import static org.assertj.core.api.Assertions.assertThat;

import com.mygrinlog.common.time.ServiceClock;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class PetStateServiceTest {

    private final ServiceClock clock = new ServiceClock(ZoneId.of("Asia/Seoul"), 6, Clock.systemUTC());
    private final PetProperties properties = new PetProperties(
            new PetProperties.Exp(2, 5, 10),
            new PetProperties.Level(2, 10),
            new PetProperties.ConditionDays(1, 3),
            new PetProperties.PetImage("/assets/giraffe", 4)
    );
    private final PetStateService service = new PetStateService(null,
            new PetImageUrlBuilder(properties), new PetStageCatalog(), properties, clock);

    @Test
    void level_caps_at_2() {
        // 스펙 §4.2 검증: exp=26 → level=2, expIntoLevel=6
        assertThat(service.computeLevel(26)).isEqualTo(2);
        assertThat(service.expIntoLevel(26)).isEqualTo(6);
    }

    @Test
    void level_does_not_exceed_cap_even_with_huge_exp() {
        assertThat(service.computeLevel(500)).isEqualTo(2);
        assertThat(service.expIntoLevel(500)).isEqualTo(0);
    }

    @Test
    void level_zero_until_threshold() {
        assertThat(service.computeLevel(9)).isZero();
        assertThat(service.computeLevel(10)).isEqualTo(1);
    }

    @Test
    void condition_good_when_active_today() {
        // 같은 서비스 일자 → daysInactive=0 → good
        Instant last = ZonedDateTime.of(2026, 5, 22, 10, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
        Instant now = ZonedDateTime.of(2026, 5, 22, 20, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
        assertThat(service.computeCondition(last, now)).isEqualTo(PetState.Condition.good);
    }

    @Test
    void condition_bad_at_2_days() {
        // 5-20 점심 활동, 5-22 점심 → serviceDate 차 2일 → bad (goodMaxDays=1)
        Instant last = ZonedDateTime.of(2026, 5, 20, 12, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
        Instant now = ZonedDateTime.of(2026, 5, 22, 12, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
        assertThat(service.computeCondition(last, now)).isEqualTo(PetState.Condition.bad);
    }

    @Test
    void condition_terrible_at_4_days() {
        // 5-18 → 5-22, serviceDate 차 4일 → terrible (badMaxDays=3)
        Instant last = ZonedDateTime.of(2026, 5, 18, 12, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
        Instant now = ZonedDateTime.of(2026, 5, 22, 12, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
        assertThat(service.computeCondition(last, now)).isEqualTo(PetState.Condition.terrible);
    }

    @Test
    void condition_good_after_record_resumed_even_if_was_terrible_cache() {
        // 가역: 마지막 활동을 방금으로 갱신하면 즉시 good
        Instant now = Instant.parse("2026-05-22T15:00:00Z");
        assertThat(service.computeCondition(now, now)).isEqualTo(PetState.Condition.good);
    }
}
