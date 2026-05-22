package com.mygrinlog.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/**
 * 스펙 §3.1 검증 케이스 3개를 그대로 옮긴 테스트.
 */
class ServiceClockTest {

    private final ServiceClock clock = new ServiceClock(ZoneId.of("Asia/Seoul"), 6, Clock.systemUTC());

    @Test
    void afternoon_returns_same_date() {
        // 05-21 14:20 KST → −6h → 05-21 08:20 → 05-21
        Instant in = ZonedDateTime.of(2026, 5, 21, 14, 20, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
        assertThat(clock.serviceDate(in)).isEqualTo(LocalDate.of(2026, 5, 21));
    }

    @Test
    void before_6am_is_still_yesterday() {
        // 05-22 05:30 KST → −6h → 05-21 23:30 → 05-21
        Instant in = ZonedDateTime.of(2026, 5, 22, 5, 30, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
        assertThat(clock.serviceDate(in)).isEqualTo(LocalDate.of(2026, 5, 21));
    }

    @Test
    void after_6am_rolls_to_today() {
        // 05-22 06:30 KST → −6h → 05-22 00:30 → 05-22
        Instant in = ZonedDateTime.of(2026, 5, 22, 6, 30, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
        assertThat(clock.serviceDate(in)).isEqualTo(LocalDate.of(2026, 5, 22));
    }

    @Test
    void exactly_6am_is_today() {
        // 05-22 06:00 KST → −6h → 05-22 00:00 → 05-22
        Instant in = ZonedDateTime.of(2026, 5, 22, 6, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();
        assertThat(clock.serviceDate(in)).isEqualTo(LocalDate.of(2026, 5, 22));
    }
}
