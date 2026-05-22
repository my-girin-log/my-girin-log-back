package com.mygrinlog.batch;

import com.mygrinlog.chat.ChatMessage;
import com.mygrinlog.chat.ChatMessageRepository;
import com.mygrinlog.chat.DailyChatSession;
import com.mygrinlog.chat.DailyChatSessionRepository;
import com.mygrinlog.common.time.ServiceClock;
import com.mygrinlog.diary.Diary;
import com.mygrinlog.diary.DiaryDraft;
import com.mygrinlog.diary.DiaryGenerator;
import com.mygrinlog.diary.DiaryRepository;
import com.mygrinlog.persona.Persona;
import com.mygrinlog.persona.PersonaRepository;
// (Persona 자체를 넘기므로 packPersonaCore 호출은 Generator 내부에서 처리)
import com.mygrinlog.pet.PetStateService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 스펙 §3.2 롤업 흐름.
 *
 * 트랜잭션 경계 (스펙 §5): OpenAI 호출은 절대 트랜잭션 안에 두지 않는다.
 *  1) loadCtx          — TX (read-only)
 *  2) generate diary   — TX 밖
 *  3) persistDiary     — TX (write)
 *
 *  Self-call 시 Spring AOP 프록시가 우회되는 함정 때문에 @Transactional 어노테이션 대신
 *  TransactionTemplate 으로 명시적으로 경계를 그었다.
 *
 *  견고함은 둘만 (스펙 §3.3):
 *   - 유저별 try/catch — 한 명 실패가 배치 전체를 죽이지 않게.
 *   - OpenAI 는 트랜잭션 밖.
 *  재시도·백오프·데드레터·락은 의도적으로 안 함.
 */
@Service
public class RollupService {

    private static final Logger log = LoggerFactory.getLogger(RollupService.class);

    private final DailyChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final PersonaRepository personaRepository;
    private final DiaryRepository diaryRepository;
    private final DiaryGenerator diaryGenerator;
    private final PetStateService petStateService;
    private final ServiceClock clock;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    public RollupService(DailyChatSessionRepository sessionRepository,
                         ChatMessageRepository messageRepository,
                         PersonaRepository personaRepository,
                         DiaryRepository diaryRepository,
                         DiaryGenerator diaryGenerator,
                         PetStateService petStateService,
                         ServiceClock clock,
                         PlatformTransactionManager txManager) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.personaRepository = personaRepository;
        this.diaryRepository = diaryRepository;
        this.diaryGenerator = diaryGenerator;
        this.petStateService = petStateService;
        this.clock = clock;
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(txManager);
    }

    /** 스케줄러와 수동 트리거가 공유하는 단일 진입점. */
    public RollupResult rollupAll() {
        LocalDate today = clock.today();
        List<DailyChatSession> targets = readTx.execute(status ->
                sessionRepository.findAllByStatusAndDateKeyLessThan(DailyChatSession.Status.active, today));

        int diariesCreated = 0;
        int emptyClosed = 0;
        List<String> failures = new ArrayList<>();

        for (DailyChatSession session : targets) {
            try {
                Outcome outcome = rollupOne(session.getId());
                if (outcome == Outcome.DIARY_CREATED) diariesCreated++;
                else if (outcome == Outcome.EMPTY_CLOSED) emptyClosed++;
            } catch (Exception e) {
                log.warn("Rollup failed for session userId={} dateKey={}: {}",
                        session.getUserId(), session.getDateKey(), e.toString());
                failures.add("user=%d date=%s err=%s".formatted(
                        session.getUserId(), session.getDateKey(), e.getMessage()));
            }
        }

        log.info("Rollup done: processed={} created={} emptyClosed={} failed={}",
                targets.size(), diariesCreated, emptyClosed, failures.size());
        return new RollupResult(targets.size(), diariesCreated, emptyClosed, failures);
    }

    Outcome rollupOne(Long sessionId) {
        SessionContext ctx = readTx.execute(status -> loadSessionContext(sessionId));
        if (ctx == null) return Outcome.SKIPPED;

        if (ctx.userMessages.isEmpty()) {
            writeTx.executeWithoutResult(status -> closeEmpty(sessionId));
            return Outcome.EMPTY_CLOSED;
        }

        // ⚠ OpenAI(Claude): 트랜잭션 밖
        DiaryDraft draft = diaryGenerator.generate(ctx.dateKey, ctx.allMessages, ctx.persona);
        // raw_text 는 백엔드가 합쳐서 넘김 (스펙 §6 카키 합의: AI 가 raw_text 를 만들지 않게).
        // DiaryDraft.rawText 가 채워져 오면 그것을 우선 사용, 비면 user 메시지 join.
        String rawText = (draft.rawText() != null && !draft.rawText().isBlank())
                ? draft.rawText() : joinUserMessages(ctx.userMessages);

        writeTx.executeWithoutResult(status -> persistDiary(sessionId, ctx.userId, ctx.dateKey, draft, rawText));
        return Outcome.DIARY_CREATED;
    }

    private SessionContext loadSessionContext(Long sessionId) {
        DailyChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getStatus() != DailyChatSession.Status.active) {
            return null;
        }
        List<ChatMessage> allMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<ChatMessage> userMessages = allMessages.stream()
                .filter(m -> m.getRole() == ChatMessage.Role.user)
                .toList();
        Persona persona = personaRepository.findByUserId(session.getUserId()).orElse(null);
        return new SessionContext(session.getUserId(), session.getDateKey(), allMessages, userMessages, persona);
    }

    private void closeEmpty(Long sessionId) {
        DailyChatSession session = sessionRepository.findById(sessionId).orElseThrow();
        session.close(clock.now());
    }

    /** 동일 (user_id, date_key) 다이어리 존재 시 덮어쓰기 (스펙 §3.3 "이미 다이어리 존재"). */
    private void persistDiary(Long sessionId, Long userId, LocalDate dateKey, DiaryDraft draft, String rawText) {
        Diary diary = diaryRepository.findByUserIdAndDateKey(userId, dateKey)
                .orElseGet(() -> new Diary(userId, dateKey, draft.title(), rawText,
                        draft.markdown(), draft.emotionEmoji(), draft.tags()));
        diary.update(draft.title(), draft.markdown(), draft.emotionEmoji(), draft.tags());
        diaryRepository.save(diary);

        DailyChatSession session = sessionRepository.findById(sessionId).orElseThrow();
        session.close(clock.now());

        petStateService.gainPerDiary(userId);
    }

    private String joinUserMessages(List<ChatMessage> userMessages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : userMessages) {
            sb.append(m.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    enum Outcome { DIARY_CREATED, EMPTY_CLOSED, SKIPPED }

    record SessionContext(
            Long userId,
            LocalDate dateKey,
            List<ChatMessage> allMessages,
            List<ChatMessage> userMessages,
            Persona persona
    ) {}
}
