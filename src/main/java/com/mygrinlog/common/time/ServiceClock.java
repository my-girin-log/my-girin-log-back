package com.mygrinlog.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 서비스 하루 경계는 06:00 KST (스펙 §0, §3.1).
 * serviceDate(instant) = LocalDate of ( instant.atZone(Asia/Seoul).minusHours(6) )
 *
 * 검증 (스펙 §3.1):
 *  - 05-21 14:20 KST → 05-21
 *  - 05-22 05:30 KST → 05-21 (아직 어제)
 *  - 05-22 06:30 KST → 05-22 (오늘로 넘어감)
 */
@Component
public class ServiceClock {

    private final ZoneId zone;
    private final int dayStartHour;
    private final Clock clock;

    @Autowired
    public ServiceClock(
            @Value("${wootegotchi.service-clock.zone:Asia/Seoul}") String zone,
            @Value("${wootegotchi.service-clock.day-start-hour:6}") int dayStartHour) {
        this(ZoneId.of(zone), dayStartHour, Clock.systemUTC());
    }

    public ServiceClock(ZoneId zone, int dayStartHour, Clock clock) {
        this.zone = zone;
        this.dayStartHour = dayStartHour;
        this.clock = clock;
    }

    public LocalDate today() {
        return serviceDate(Instant.now(clock));
    }

    public LocalDate serviceDate(Instant instant) {
        return instant.atZone(zone).minusHours(dayStartHour).toLocalDate();
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public ZoneId zone() {
        return zone;
    }

    public int dayStartHour() {
        return dayStartHour;
    }
}
