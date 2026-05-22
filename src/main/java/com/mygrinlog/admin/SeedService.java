package com.mygrinlog.admin;

import com.mygrinlog.common.time.ServiceClock;
import com.mygrinlog.diary.Diary;
import com.mygrinlog.diary.DiaryRepository;
import com.mygrinlog.retrospective.Retrospective;
import com.mygrinlog.retrospective.RetrospectiveRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신규 유저 자동 시드.
 *
 *  - 페르소나는 박지 않음 → 사용자가 온보딩 거치며 자기 어조로 생성.
 *  - 다이어리 7개 + 회고 1개만 박음 → 메인 진입 즉시 UI 확인 가능.
 *  - 펫 EXP 변경 X → LV.0 calf 시작, 사용자가 "레벨업" 버튼으로 진화 시연.
 *
 *  AuthController.upsertUser 가 새 유저 생성 시 호출. 기존 유저는 안 건드림.
 *  여러 사용자가 동시 시연해도 각자 자기 유저에 데이터 박힘 (사용자 격리).
 */
@Service
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);

    private final DiaryRepository diaryRepository;
    private final RetrospectiveRepository retrospectiveRepository;
    private final ServiceClock clock;

    public SeedService(DiaryRepository diaryRepository,
                       RetrospectiveRepository retrospectiveRepository,
                       ServiceClock clock) {
        this.diaryRepository = diaryRepository;
        this.retrospectiveRepository = retrospectiveRepository;
        this.clock = clock;
    }

    @Transactional
    public void seedNewUser(Long userId) {
        LocalDate today = clock.today();

        for (AdminController.DiarySeedExposed seed : AdminController.publicSeeds()) {
            LocalDate dateKey = today.plusDays(seed.dayOffset());
            if (diaryRepository.existsByUserIdAndDateKey(userId, dateKey)) continue;
            diaryRepository.save(new Diary(
                    userId, dateKey, seed.title(),
                    seed.markdown().replaceAll("[#*`>\\-]", "").replaceAll("\\s+", " ").trim(),
                    seed.markdown(), seed.emoji(), seed.tags()));
        }

        List<Long> diaryIds = diaryRepository
                .findAllByUserIdAndDateKeyBetweenOrderByDateKeyAsc(userId, today.minusDays(7), today.minusDays(1))
                .stream().map(Diary::getId).toList();

        retrospectiveRepository.save(new Retrospective(
                userId,
                "이번 주 트러블슈팅 회고 (샘플)",
                """
                        # 이번 주 트러블슈팅 회고 (샘플)

                        ## 무슨 일이 있었나
                        이번 주 가장 큰 시간 소비는 Spring AI Anthropic 통합. 1.0.0-M3 의 builder API 가
                        예상과 달라서 한참 헤맸음. javap 로 jar 까보고 해결.

                        ## 패턴 발견
                        - 의존성 마일스톤 버전은 IDE 자동완성과 어긋날 수 있다
                        - `@Transactional` self-call 함정은 한 번 더 걸렸음. TransactionTemplate 으로 명시화하니 깔끔

                        ## 다음 주
                        - 외부 호출이 트랜잭션 안에 있는지 체크리스트
                        - 새 의존성 도입 시 javap 한 번 까보기

                        ---
                        *이 회고는 데모용 샘플입니다. 본인이 다이어리를 추가하고 회고를 생성하면 자기 어조로 진짜 회고가 만들어집니다.*
                        """,
                "Spring AI 통합 삽질 + 트랜잭션 함정 — 다음 주 체크리스트.",
                List.of("우테코", "Spring", "트러블슈팅", "샘플"),
                Retrospective.Type.woowacourse,
                Map.of("focus", "트러블슈팅", "length", "medium"),
                today.minusDays(7), today.minusDays(1),
                diaryIds
        ));

        log.info("[SeedService] seeded new user {}: 7 diaries + 1 retrospective (persona 없음, EXP 안 박음)", userId);
    }
}
