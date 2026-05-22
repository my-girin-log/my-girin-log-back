package com.mygrinlog.retrospective;

import com.mygrinlog.auth.CurrentUser;
import com.mygrinlog.common.web.ApiPaths;
import com.mygrinlog.common.web.NotFoundException;
import com.mygrinlog.diary.Diary;
import com.mygrinlog.persona.Persona;
import com.mygrinlog.pet.PetStateService;
import com.mygrinlog.pet.PetView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * 워넬 자료 §2.4 + 스펙 §6 워넬 합의 (PUT 추가).
 *  - POST   /retrospectives        : 기간/옵션 → 3-pass LLM → 저장 + EXP+10.
 *  - GET    /retrospectives        : 내 회고 목록 (최신순).
 *  - GET    /retrospectives/{id}   : 회고 상세.
 *  - PUT    /retrospectives/{id}   : 수정 (Frame 9 수정 버튼).
 *  - DELETE /retrospectives/{id}   : 삭제.
 *
 *  sourceDiaryIds 는 항상 백엔드가 주입 (스펙 §5).
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/retrospectives")
public class RetrospectiveController {

    private final RetrospectiveService retrospectiveService;
    private final RetrospectiveGenerator retrospectiveGenerator;
    private final PetStateService petStateService;

    public RetrospectiveController(RetrospectiveService retrospectiveService,
                                   RetrospectiveGenerator retrospectiveGenerator,
                                   PetStateService petStateService) {
        this.retrospectiveService = retrospectiveService;
        this.retrospectiveGenerator = retrospectiveGenerator;
        this.petStateService = petStateService;
    }

    @PostMapping
    public CreateResponse create(@CurrentUser Long userId, @RequestBody CreateRequest request) {
        if (request == null || request.type() == null || request.rangeStartDate() == null || request.rangeEndDate() == null) {
            throw new IllegalArgumentException("type / rangeStartDate / rangeEndDate 는 필수");
        }
        if (request.rangeEndDate().isBefore(request.rangeStartDate())) {
            throw new IllegalArgumentException("rangeEndDate 가 rangeStartDate 보다 빠를 수 없음");
        }
        // 1) Diary 조회 (TX)
        List<Diary> diaries = retrospectiveService.diariesInRange(userId, request.rangeStartDate(), request.rangeEndDate());
        if (diaries.isEmpty()) {
            throw new IllegalArgumentException("해당 기간에 다이어리가 없음");
        }
        Persona persona = retrospectiveService.findPersona(userId);

        // 2) LLM 3-pass (TX 밖)
        RetrospectiveDraft draft = retrospectiveGenerator.generate(
                request.type(),
                request.rangeStartDate(),
                request.rangeEndDate(),
                request.promptOptions(),
                diaries,
                persona
        );

        // 3) 저장 (TX) — sourceDiaryIds 는 여기서 주입
        List<Long> sourceDiaryIds = diaries.stream().map(Diary::getId).toList();
        Retrospective saved = retrospectiveService.persist(
                userId, request.type(),
                request.rangeStartDate(), request.rangeEndDate(),
                request.promptOptions(),
                sourceDiaryIds, draft);

        PetView pet = petStateService.view(userId);
        return new CreateResponse(saved.getId(), saved.getTitle(), saved.getMarkdown(),
                saved.getSummary(), saved.getTags(), sourceDiaryIds, pet);
    }

    @GetMapping
    public ListResponse list(@CurrentUser Long userId) {
        List<RetrospectiveListItem> items = retrospectiveService.listForUser(userId).stream()
                .map(RetrospectiveListItem::from)
                .toList();
        return new ListResponse(items);
    }

    @GetMapping("/{id}")
    public RetrospectiveDetail get(@CurrentUser Long userId, @PathVariable Long id) {
        Retrospective r = retrospectiveService.findOwned(userId, id);
        if (r == null) throw new NotFoundException("retrospective not found: " + id);
        return RetrospectiveDetail.from(r);
    }

    @PutMapping("/{id}")
    public RetrospectiveDetail update(@CurrentUser Long userId, @PathVariable Long id,
                                      @RequestBody UpdateRequest request) {
        Retrospective r = retrospectiveService.update(userId, id,
                request.title(), request.markdown(), request.summary(), request.tags());
        if (r == null) throw new NotFoundException("retrospective not found: " + id);
        return RetrospectiveDetail.from(r);
    }

    @DeleteMapping("/{id}")
    public void delete(@CurrentUser Long userId, @PathVariable Long id) {
        if (!retrospectiveService.delete(userId, id)) {
            throw new NotFoundException("retrospective not found: " + id);
        }
    }

    public record CreateRequest(
            Retrospective.Type type,
            LocalDate rangeStartDate,
            LocalDate rangeEndDate,
            Map<String, Object> promptOptions
    ) {}

    public record CreateResponse(
            Long retrospectiveId,
            String title,
            String markdown,
            String summary,
            List<String> tags,
            List<Long> sourceDiaryIds,
            PetView petUpdate
    ) {}

    public record UpdateRequest(String title, String markdown, String summary, List<String> tags) {}

    public record ListResponse(List<RetrospectiveListItem> retrospectives) {}

    public record RetrospectiveListItem(
            Long id,
            String title,
            String summary,
            List<String> tags,
            Retrospective.Type type,
            LocalDate rangeStartDate,
            LocalDate rangeEndDate,
            Instant createdAt
    ) {
        static RetrospectiveListItem from(Retrospective r) {
            return new RetrospectiveListItem(r.getId(), r.getTitle(), r.getSummary(), r.getTags(),
                    r.getType(), r.getRangeStartDate(), r.getRangeEndDate(), r.getCreatedAt());
        }
    }

    public record RetrospectiveDetail(
            Long id,
            String title,
            String markdown,
            String summary,
            List<String> tags,
            Retrospective.Type type,
            Map<String, Object> promptOptions,
            LocalDate rangeStartDate,
            LocalDate rangeEndDate,
            List<Long> sourceDiaryIds,
            Instant createdAt,
            Instant updatedAt
    ) {
        static RetrospectiveDetail from(Retrospective r) {
            return new RetrospectiveDetail(
                    r.getId(), r.getTitle(), r.getMarkdown(), r.getSummary(), r.getTags(),
                    r.getType(), r.getPromptOptions(),
                    r.getRangeStartDate(), r.getRangeEndDate(),
                    r.getSourceDiaryIds(),
                    r.getCreatedAt(), r.getUpdatedAt());
        }
    }
}
