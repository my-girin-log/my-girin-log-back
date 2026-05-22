package com.mygrinlog.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<ChatMessage> findBySessionIdAndRoleOrderByCreatedAtAsc(Long sessionId, ChatMessage.Role role);

    long countBySessionIdAndRole(Long sessionId, ChatMessage.Role role);
}
