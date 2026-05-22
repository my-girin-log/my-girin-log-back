package com.mygrinlog.chat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyChatSessionRepository extends JpaRepository<DailyChatSession, Long> {

    Optional<DailyChatSession> findByUserIdAndDateKey(Long userId, LocalDate dateKey);

    Optional<DailyChatSession> findFirstByUserIdAndStatusOrderByStartedAtDesc(Long userId, DailyChatSession.Status status);

    List<DailyChatSession> findAllByStatusAndDateKeyLessThan(DailyChatSession.Status status, LocalDate dateKey);
}
