package com.mygrinlog.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 스펙 §3.2: cron + 수동 트리거가 같은 메서드를 공유. */
@Component
public class RollupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RollupScheduler.class);

    private final RollupService rollupService;

    public RollupScheduler(RollupService rollupService) {
        this.rollupService = rollupService;
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void runDaily() {
        log.info("[RollupScheduler] 06:00 KST tick — starting rollup");
        RollupResult result = rollupService.rollupAll();
        log.info("[RollupScheduler] result={}", result);
    }
}
