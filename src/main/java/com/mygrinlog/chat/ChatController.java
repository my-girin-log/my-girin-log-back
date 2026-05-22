package com.mygrinlog.chat;

import com.mygrinlog.auth.CurrentUser;
import com.mygrinlog.common.web.ApiPaths;
import com.mygrinlog.persona.Persona;
import com.mygrinlog.pet.PetView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/**
 * 워넬 자료 §2.2.
 *  - GET  /chats/active   : 오늘(=06:00 KST 기준 서비스 일자) 활성 세션 + 이력.
 *  - POST /chats/message  : 메시지 전송 → AI 역질문 → 양쪽 저장 → 펫 EXP+2.
 *
 *  메시지 전송 흐름 (스펙 §5):
 *    user 저장+EXP (TX) → ReverseQuestionGenerator (TX 밖) → assistant 저장 (TX).
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/chats")
public class ChatController {

    private final ChatService chatService;
    private final ReverseQuestionGenerator questionGenerator;

    public ChatController(ChatService chatService, ReverseQuestionGenerator questionGenerator) {
        this.chatService = chatService;
        this.questionGenerator = questionGenerator;
    }

    @GetMapping("/active")
    public ActiveSessionResponse active(@CurrentUser Long userId) {
        DailyChatSession session = chatService.findOrCreateActiveSession(userId);
        List<ChatMessage> messages = chatService.recentMessages(session.getId());
        return new ActiveSessionResponse(
                new SessionView(session.getId(), session.getDateKey(), session.getStatus().name(), session.getStartedAt()),
                messages.stream().map(MessageView::from).toList()
        );
    }

    @PostMapping("/message")
    public PostMessageResponse postMessage(@CurrentUser Long userId, @RequestBody PostMessageRequest request) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        Long sessionId = request.sessionId() != null
                ? request.sessionId()
                : chatService.findOrCreateActiveSession(userId).getId();

        ChatMessage.Source source = request.source() != null ? request.source() : ChatMessage.Source.typed;

        // 1) user 저장 + EXP+2 (TX)
        ChatService.PostUserMessageResult userResult =
                chatService.appendUserMessage(userId, sessionId, request.content(), source);

        // 2) LLM 호출 (TX 밖)
        Persona persona = chatService.findPersona(userId);
        List<ChatMessage> history = chatService.recentMessages(sessionId);
        String assistantContent;
        try {
            assistantContent = questionGenerator.generate(history, persona);
        } catch (Exception e) {
            // 한 메시지의 AI 실패가 유저 기록을 막아서는 안 된다. user 저장은 이미 커밋됨.
            assistantContent = "(역질문 생성 일시 실패 — 잠시 후 다시 시도해 줘.)";
        }

        // 3) assistant 저장 (TX)
        ChatMessage assistant = chatService.appendAssistantMessage(sessionId, assistantContent);

        PetView pet = chatService.petView(userId);
        return new PostMessageResponse(
                MessageView.from(userResult.userMessage()),
                MessageView.from(assistant),
                2,  // wootegotchi.exp.per-message — 워넬 자료의 petExpGained
                pet.exp(),
                pet
        );
    }

    public record PostMessageRequest(Long sessionId, String content, ChatMessage.Source source) {}

    public record PostMessageResponse(
            MessageView userMessage,
            MessageView assistantMessage,
            int petExpGained,
            int currentPetExp,
            PetView pet
    ) {}

    public record ActiveSessionResponse(SessionView session, List<MessageView> messages) {}

    public record SessionView(Long id, LocalDate dateKey, String status, Instant startedAt) {}

    public record MessageView(Long id, String role, String content, String source, Instant createdAt) {
        static MessageView from(ChatMessage m) {
            return new MessageView(m.getId(), m.getRole().name(), m.getContent(),
                    m.getSource().name(), m.getCreatedAt());
        }
    }
}
