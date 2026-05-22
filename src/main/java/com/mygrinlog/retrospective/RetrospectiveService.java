package com.mygrinlog.retrospective;

import com.mygrinlog.diary.Diary;
import com.mygrinlog.diary.DiaryRepository;
import com.mygrinlog.persona.Persona;
import com.mygrinlog.persona.PersonaRepository;
import com.mygrinlog.pet.PetStateService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회고 생성 트랜잭션 경계.
 *  - loadDiaries (TX)
 *  - ←—— 컨트롤러가 RetrospectiveGenerator (3-pass LLM, 트랜잭션 밖) 호출 ——→
 *  - persist (TX): Retrospective 저장 + EXP+10 + sourceDiaryIds 백엔드 주입 (스펙 §5).
 */
@Service
public class RetrospectiveService {

    private final RetrospectiveRepository retrospectiveRepository;
    private final DiaryRepository diaryRepository;
    private final PersonaRepository personaRepository;
    private final PetStateService petStateService;

    public RetrospectiveService(RetrospectiveRepository retrospectiveRepository,
                                DiaryRepository diaryRepository,
                                PersonaRepository personaRepository,
                                PetStateService petStateService) {
        this.retrospectiveRepository = retrospectiveRepository;
        this.diaryRepository = diaryRepository;
        this.personaRepository = personaRepository;
        this.petStateService = petStateService;
    }

    @Transactional(readOnly = true)
    public List<Diary> diariesInRange(Long userId, LocalDate from, LocalDate to) {
        return diaryRepository.findAllByUserIdAndDateKeyBetweenOrderByDateKeyAsc(userId, from, to);
    }

    @Transactional(readOnly = true)
    public Persona findPersona(Long userId) {
        return personaRepository.findByUserId(userId).orElse(null);
    }

    /** AI 결과 + 컨텍스트 → DB 저장. sourceDiaryIds 는 호출자가 넘기는 그대로 백엔드 주입. */
    @Transactional
    public Retrospective persist(Long userId, Retrospective.Type type,
                                 LocalDate rangeStart, LocalDate rangeEnd,
                                 Map<String, Object> promptOptions,
                                 List<Long> sourceDiaryIds,
                                 RetrospectiveDraft draft) {
        Retrospective entity = new Retrospective(
                userId, draft.title(), draft.markdown(), draft.summary(), draft.tags(),
                type, promptOptions, rangeStart, rangeEnd, sourceDiaryIds);
        Retrospective saved = retrospectiveRepository.save(entity);
        petStateService.gainPerRetrospective(userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Retrospective> listForUser(Long userId) {
        return retrospectiveRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Retrospective findOwned(Long userId, Long id) {
        Retrospective r = retrospectiveRepository.findById(id).orElse(null);
        if (r == null || !r.getUserId().equals(userId)) return null;
        return r;
    }

    @Transactional
    public Retrospective update(Long userId, Long id, String title, String markdown,
                                String summary, List<String> tags) {
        Retrospective r = findOwned(userId, id);
        if (r == null) return null;
        r.update(
                title == null ? r.getTitle() : title,
                markdown == null ? r.getMarkdown() : markdown,
                summary == null ? r.getSummary() : summary,
                tags == null ? r.getTags() : tags
        );
        return r;
    }

    @Transactional
    public boolean delete(Long userId, Long id) {
        Retrospective r = findOwned(userId, id);
        if (r == null) return false;
        retrospectiveRepository.delete(r);
        return true;
    }
}
