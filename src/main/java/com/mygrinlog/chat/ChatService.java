package com.mygrinlog.chat;

import com.mygrinlog.common.time.ServiceClock;
import com.mygrinlog.persona.Persona;
import com.mygrinlog.persona.PersonaRepository;
import com.mygrinlog.pet.PetState;
import com.mygrinlog.pet.PetStateService;
import com.mygrinlog.pet.PetView;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅 도메인의 트랜잭션 경계.
 *  - 컨트롤러가 이 메서드들 사이에서 ReverseQuestionGenerator (LLM, 트랜잭션 밖) 를 호출.
 *  - 1) 활성 세션 조회/생성 (TX)
 *    2) user 메시지 저장 + EXP +2 (TX)
 *    3) ←—— 컨트롤러가 LLM 호출 ——→
 *    4) assistant 메시지 저장 (TX)
 *
 *  스펙 §5: 메시지 전송은 user 저장+EXP 가 AI 성공과 무관하게 적립되어야 함.
 *           따라서 (2) 와 (4) 는 별도 트랜잭션.
 */
@Service
public class ChatService {

    private final DailyChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final PersonaRepository personaRepository;
    private final PetStateService petStateService;
    private final ServiceClock clock;

    public ChatService(DailyChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository,
                       PersonaRepository personaRepository,
                       PetStateService petStateService,
                       ServiceClock clock) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.personaRepository = personaRepository;
        this.petStateService = petStateService;
        this.clock = clock;
    }

    @Transactional
    public DailyChatSession findOrCreateActiveSession(Long userId) {
        return sessionRepository.findByUserIdAndDateKey(userId, clock.today())
                .orElseGet(() -> sessionRepository.save(
                        new DailyChatSession(userId, clock.today(), clock.now())));
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> recentMessages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional(readOnly = true)
    public Persona findPersona(Long userId) {
        return personaRepository.findByUserId(userId).orElse(null);
    }

    /** user 메시지 저장 + EXP +2. AI 성공과 무관하게 적립 (스펙 §5). */
    @Transactional
    public PostUserMessageResult appendUserMessage(Long userId, Long sessionId, String content, ChatMessage.Source source) {
        DailyChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found: " + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("session does not belong to current user");
        }
        if (session.getStatus() != DailyChatSession.Status.active) {
            throw new IllegalStateException("session is not active (already rolled up)");
        }
        ChatMessage saved = messageRepository.save(new ChatMessage(
                sessionId, ChatMessage.Role.user, content, source, clock.now()));
        PetState pet = petStateService.gainPerMessage(userId);
        return new PostUserMessageResult(saved, pet);
    }

    @Transactional
    public ChatMessage appendAssistantMessage(Long sessionId, String content) {
        return messageRepository.save(new ChatMessage(
                sessionId, ChatMessage.Role.assistant, content, ChatMessage.Source.typed, clock.now()));
    }

    public PetView petView(Long userId) {
        return petStateService.view(userId);
    }

    public record PostUserMessageResult(ChatMessage userMessage, PetState pet) {}
}
