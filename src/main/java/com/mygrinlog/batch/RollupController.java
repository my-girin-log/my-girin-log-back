package com.mygrinlog.batch;

import com.mygrinlog.auth.CurrentUser;
import com.mygrinlog.common.web.ApiPaths;
import com.mygrinlog.common.time.ServiceClock;
import com.mygrinlog.diary.Diary;
import com.mygrinlog.diary.DiaryRepository;
import com.mygrinlog.pet.PetStateService;
import com.mygrinlog.pet.PetView;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.web.bind.annotation.*;

/**
 * 워넬 자료 §2.3 의 수동 롤업 트리거.
 *  - POST /api/v1/diaries/rollup
 *    응답: success / message / generatedDiaryId / petUpdate (PetView).
 *
 *  스펙 §3.2 — 스케줄러와 같은 메서드(RollupService#rollupAll) 사용.
 *  현재 로그인 유저의 어제 dateKey 다이어리 한 건의 id 를 응답에 포함 (있으면).
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/diaries")
public class RollupController {

    private final RollupService rollupService;
    private final DiaryRepository diaryRepository;
    private final PetStateService petStateService;
    private final ServiceClock clock;

    public RollupController(RollupService rollupService, DiaryRepository diaryRepository,
                            PetStateService petStateService, ServiceClock clock) {
        this.rollupService = rollupService;
        this.diaryRepository = diaryRepository;
        this.petStateService = petStateService;
        this.clock = clock;
    }

    @PostMapping("/rollup")
    public RollupResponse triggerRollup(@CurrentUser Long userId) {
        RollupResult result = rollupService.rollupAll();

        LocalDate yesterday = clock.today().minusDays(1);
        Long diaryId = diaryRepository.findByUserIdAndDateKey(userId, yesterday)
                .map(Diary::getId)
                .orElse(null);

        boolean success = result.failures().isEmpty();
        String message = "%s %d 데일리 마이그레이션 완료 (created=%d, emptyClosed=%d, failed=%d)"
                .formatted(yesterday, result.processedSessions(),
                        result.diariesCreated(), result.emptySessionsClosed(), result.failures().size());

        return new RollupResponse(success, message, diaryId, petStateService.view(userId), result);
    }

    public record RollupResponse(boolean success, String message, Long generatedDiaryId,
                                 PetView petUpdate, RollupResult batchResult) {}
}
