package com.mygrinlog.chat;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "daily_chat_session",
        uniqueConstraints = @UniqueConstraint(name = "uk_session_user_date", columnNames = {"user_id", "date_key"})
)
public class DailyChatSession {

    public enum Status { active, rolled_up }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "date_key", nullable = false)
    private LocalDate dateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected DailyChatSession() {}

    public DailyChatSession(Long userId, LocalDate dateKey, Instant startedAt) {
        this.userId = userId;
        this.dateKey = dateKey;
        this.status = Status.active;
        this.startedAt = startedAt;
    }

    public void close(Instant closedAt) {
        this.status = Status.rolled_up;
        this.closedAt = closedAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getDateKey() { return dateKey; }
    public Status getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getClosedAt() { return closedAt; }
}
