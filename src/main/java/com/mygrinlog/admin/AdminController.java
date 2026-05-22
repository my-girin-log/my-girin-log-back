package com.mygrinlog.admin;

import com.mygrinlog.auth.CurrentUser;
import com.mygrinlog.common.time.ServiceClock;
import com.mygrinlog.common.web.ApiPaths;
import com.mygrinlog.diary.Diary;
import com.mygrinlog.diary.DiaryRepository;
import com.mygrinlog.pet.PetStateService;
import com.mygrinlog.pet.PetView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 시연/QA 용 어드민 엔드포인트. 운영 도입 시 인증 강화 또는 제거 필요.
 *
 *  POST /api/v1/admin/seed-week
 *    현재 로그인 유저의 어제(-1) ~ 7일 전(-7) 다이어리 7개를 한 번에 박는다.
 *    같은 dateKey 다이어리 이미 있으면 update (idempotent).
 *    LLM 호출 0 — 미리 박힌 더미 markdown 만 저장하므로 키/모델 권한과 무관.
 *    EXP 35 (7 × 5) 적립 → 펫 level 2 adult 도달.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/admin")
public class AdminController {

    private final DiaryRepository diaryRepository;
    private final PetStateService petStateService;
    private final ServiceClock clock;

    public AdminController(DiaryRepository diaryRepository,
                           PetStateService petStateService,
                           ServiceClock clock) {
        this.diaryRepository = diaryRepository;
        this.petStateService = petStateService;
        this.clock = clock;
    }

    @PostMapping("/seed-week")
    @Transactional
    public SeedResponse seedWeek(@CurrentUser Long userId) {
        LocalDate today = clock.today();
        List<LocalDate> seededDates = new ArrayList<>();

        for (DiarySeed seed : DUMMY_SEEDS) {
            LocalDate dateKey = today.plusDays(seed.dayOffset());
            Diary diary = diaryRepository.findByUserIdAndDateKey(userId, dateKey)
                    .map(existing -> {
                        existing.update(seed.title(), seed.markdown(), seed.emoji(), seed.tags());
                        return existing;
                    })
                    .orElseGet(() -> new Diary(userId, dateKey, seed.title(),
                            stripMarkdown(seed.markdown()), seed.markdown(),
                            seed.emoji(), seed.tags()));
            diaryRepository.save(diary);
            seededDates.add(dateKey);
            petStateService.gainPerDiary(userId);
        }

        return new SeedResponse(seededDates.size(), seededDates, petStateService.view(userId));
    }

    /**
     * 데모용 EXP 즉시 주입. 프론트 "레벨업" 버튼이 호출.
     *  - body 없으면 +10 (다음 레벨까지 보통 정확히 한 step)
     *  - body {amount: N} 으로 명시 가능
     *
     * 응답은 갱신된 PetView. 프론트가 setPet 으로 받으면 sprite 가 자동으로 다음 stage 로 진화.
     */
    @PostMapping("/grant-exp")
    @Transactional
    public PetView grantExp(@CurrentUser Long userId, @RequestBody(required = false) GrantExpRequest request) {
        int amount = (request != null && request.amount() != null) ? request.amount() : 10;
        if (amount <= 0 || amount > 100) {
            throw new IllegalArgumentException("amount 는 1~100 범위 (현재: " + amount + ")");
        }
        petStateService.gainExp(userId, amount);
        return petStateService.view(userId);
    }

    public record GrantExpRequest(Integer amount) {}

    /** raw_text 컬럼은 markdown 헤더/이모지 제거한 평문. 검색 등에서 사용. */
    private static String stripMarkdown(String md) {
        return md.replaceAll("[#*`>\\-]", "").replaceAll("\\s+", " ").trim();
    }

    public record SeedResponse(int seeded, List<LocalDate> dateKeys, PetView petUpdate) {}

    /** DemoBootstrapper 가 쓸 수 있게 공개된 시드 record. */
    public record DiarySeedExposed(int dayOffset, String title, String markdown, String emoji, List<String> tags) {}

    /** DemoBootstrapper 가 시드 데이터 재사용하도록 노출. */
    public static List<DiarySeedExposed> publicSeeds() {
        return DUMMY_SEEDS.stream()
                .map(s -> new DiarySeedExposed(s.dayOffset(), s.title(), s.markdown(), s.emoji(), s.tags()))
                .toList();
    }

    private record DiarySeed(int dayOffset, String title, String markdown, String emoji, List<String> tags) {}

    // 어제(-1) → 7일 전(-7). 가장 가까운 날짜가 가장 최근 학습.
    private static final List<DiarySeed> DUMMY_SEEDS = List.of(
            new DiarySeed(-1,
                    "TransactionTemplate 로 리팩토링한 날",
                    """
                            ## 무슨 일이 있었나요
                            - `@Transactional` 의 self-call 함정에 또 걸렸음. 같은 빈 안에서 호출하면 프록시 우회되는 거 까먹고 있었음.
                            - 결국 `TransactionTemplate` 으로 read TX → 외부 호출 → write TX 분리하니 깔끔해짐.

                            ## 그때 든 생각
                            - 솔직히 어노테이션이 마법처럼 동작하는 줄 알았는데, 동작 원리를 모르면 함정이 많다.
                            - 코드 양은 늘었지만 "어디서 트랜잭션이 열리는지" 가 명시적이라 디버깅 편함.

                            ## 다음에 다시 본다면
                            - 외부 API 호출이 트랜잭션 안에 있는지 항상 먼저 점검.
                            """,
                    "💡",
                    List.of("Spring", "트랜잭션", "리팩토링")),

            new DiarySeed(-2,
                    "회고 작성 시간을 확보한 날",
                    """
                            ## 무슨 일이 있었나요
                            - 매일 저녁 30분, 그날 배운 걸 정리하는 루틴 시작.
                            - 처음엔 어색했지만 이틀 만에 익숙해짐.

                            ## 그때 든 생각
                            - 기록 안 하면 어제 뭐 했는지 흐릿해진다는 걸 새삼 느낌.
                            - 회고가 또 다른 학습이 된다.
                            """,
                    "✨",
                    List.of("회고", "루틴", "메타학습")),

            new DiarySeed(-3,
                    "Spring AI Anthropic 통합 삽질",
                    """
                            ## 무슨 일이 있었나요
                            - `AnthropicChatOptions.builder().withModel(...).withMaxTokens(...)` 가 1.0.0-M3 시점 API.
                            - `.model(...)` 으로 자꾸 부르다가 컴파일 에러. 마일스톤 버전이라 API 가 미세하게 다름.
                            - `javap` 로 builder 메서드 시그니처 확인하고 해결.

                            ## 그때 든 생각
                            - 의존성 버전과 IDE 자동완성이 어긋날 수 있다. 진짜 jar 의 메서드 확인이 빠를 때 있음.

                            ## 단서
                            - Spring AI 1.0.0 GA 나오면 builder 메서드 이름 바뀔 수 있음. 마이그레이션 시 주의.
                            """,
                    "🔥",
                    List.of("SpringAI", "Anthropic", "트러블슈팅")),

            new DiarySeed(-4,
                    "JPA N+1 문제를 발견한 날",
                    """
                            ## 무슨 일이 있었나요
                            - `Diary` 목록 조회 시 SQL 로그에 select 가 N+1번 떠 있는 거 발견.
                            - `@EntityGraph` 또는 fetch join 으로 해결.

                            ## 그때 든 생각
                            - `show-sql=true` 로 켜 둔 게 결국 도움이 됨. 운영에선 끄지만 개발 중에는 유용.
                            - "정상 동작" 과 "성능 정상" 은 다르다.
                            """,
                    "🤔",
                    List.of("JPA", "성능", "디버깅")),

            new DiarySeed(-5,
                    "페어 리뷰에서 의견 충돌한 날",
                    """
                            ## 무슨 일이 있었나요
                            - PR 리뷰에서 컨벤션 가지고 길게 이야기함.
                            - 내 의견과 페어 의견이 달라서 처음엔 답답했음. 결국 컨벤션 문서를 같이 다시 읽으며 합의.

                            ## 그때 든 생각
                            - 솔직히 좀 답답했는데, 끝나고 보니 둘 다 더 명확해짐.
                            - 의견 충돌이 나쁜 게 아니라 정리되지 않은 합의가 나쁜 것.

                            ## 다음에 다시 본다면
                            - PR 올리기 전에 컨벤션 문서 한 번 더 확인.
                            """,
                    "😮‍💨",
                    List.of("우테코", "페어", "코드리뷰")),

            new DiarySeed(-6,
                    "useCallback 과 useMemo 차이를 정리한 날",
                    """
                            ## 무슨 일이 있었나요
                            - 리액트 렌더링 최적화 강의 시청. `useCallback` 은 함수 메모이제이션, `useMemo` 는 값 메모이제이션.
                            - 무지성으로 두르지 말고 진짜 병목 확인 후 적용.

                            ## 그때 든 생각
                            - 성능 최적화는 측정 → 적용 → 재측정의 사이클이 본질.
                            - "이거 쓰면 빨라진다" 같은 단순 규칙은 위험함.
                            """,
                    "🌱",
                    List.of("React", "성능", "메모이제이션")),

            new DiarySeed(-7,
                    "로또 미션 예외 처리 위치 고민",
                    """
                            ## 무슨 일이 있었나요
                            - 비즈니스 로직에서 숫자가 6개가 안 넘을 때 예외를 어디서 던질지 한참 헤맸음.
                            - 결국 도메인 객체 생성자에서 던지기로 결정. 객체가 자신의 불변식을 책임지게.

                            ## 그때 든 생각
                            - 예외 처리는 "어디서 던지는가" 가 곧 "누구의 책임인가" 다.
                            - 객체지향이 추상적 개념이 아니라 진짜 코드 위치 결정의 문제임을 체감.

                            ## 다음에 다시 본다면
                            - 도메인 객체 생성자에서 불변식 검증 → 일관된 규칙으로 정착시키기.
                            """,
                    "🎯",
                    List.of("우테코", "Java", "객체지향", "예외처리"))
    );
}
