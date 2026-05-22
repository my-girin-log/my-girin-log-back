package com.mygrinlog.chat;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_message", indexes = @Index(name = "idx_msg_session_created", columnList = "session_id, created_at"))
public class ChatMessage {

    public enum Role { user, assistant }

    public enum Source { typed, voice, mock }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private Source source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessage() {}

    public ChatMessage(Long sessionId, Role role, String content, Source source, Instant createdAt) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.source = source;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public Source getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
}
