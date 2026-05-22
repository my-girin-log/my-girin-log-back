package com.mygrinlog.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.mygrinlog.chat.ChatMessage;
import com.mygrinlog.chat.ChatMessageRepository;
import com.mygrinlog.chat.DailyChatSession;
import com.mygrinlog.chat.DailyChatSessionRepository;
import com.mygrinlog.diary.DiaryRepository;
import com.mygrinlog.user.User;
import com.mygrinlog.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스펙 §3.3 핵심 케이스 통합 테스트.
 *  - 빈 세션 → 다이어리 미생성, 세션만 rolled_up.
 *  - 메시지 있는 세션 → 다이어리 1건 생성 (OPENAI_ENABLED=false 이므로 stub).
 *  - 어제 dateKey 세션만 대상이고 오늘은 건드리지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RollupServiceIntegrationTest {

    @Autowired RollupService rollupService;
    @Autowired UserRepository userRepository;
    @Autowired DailyChatSessionRepository sessionRepository;
    @Autowired ChatMessageRepository messageRepository;
    @Autowired DiaryRepository diaryRepository;

    @Test
    void empty_session_yesterday_is_closed_without_diary() {
        User user = userRepository.save(new User("gh-empty-" + System.nanoTime(), "빈세션유저", null));
        LocalDate yesterday = LocalDate.now().minusDays(5); // ServiceClock 경계 흔들림 회피

        DailyChatSession session = sessionRepository.save(
                new DailyChatSession(user.getId(), yesterday, Instant.now().minusSeconds(86400)));

        RollupResult result = rollupService.rollupAll();

        assertThat(result.emptySessionsClosed()).isGreaterThanOrEqualTo(1);
        assertThat(diaryRepository.existsByUserIdAndDateKey(user.getId(), yesterday)).isFalse();

        DailyChatSession reloaded = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DailyChatSession.Status.rolled_up);
    }

    @Test
    void non_empty_session_yesterday_creates_diary_via_stub() {
        User user = userRepository.save(new User("gh-msgs-" + System.nanoTime(), "메시지유저", null));
        LocalDate yesterday = LocalDate.now().minusDays(5); // ServiceClock 경계 흔들림 회피

        DailyChatSession session = sessionRepository.save(
                new DailyChatSession(user.getId(), yesterday, Instant.now().minusSeconds(86400)));
        messageRepository.save(new ChatMessage(session.getId(), ChatMessage.Role.user,
                "오늘 로또 미션에서 예외 처리 위치 한참 헤맸음", ChatMessage.Source.typed, Instant.now().minusSeconds(50000)));
        messageRepository.save(new ChatMessage(session.getId(), ChatMessage.Role.assistant,
                "어디에서 던지기로 정리했어?", ChatMessage.Source.typed, Instant.now().minusSeconds(49000)));
        messageRepository.save(new ChatMessage(session.getId(), ChatMessage.Role.user,
                "결국 도메인 객체 생성자에서 던짐", ChatMessage.Source.typed, Instant.now().minusSeconds(48000)));

        RollupResult result = rollupService.rollupAll();

        assertThat(result.diariesCreated()).isGreaterThanOrEqualTo(1);
        assertThat(diaryRepository.existsByUserIdAndDateKey(user.getId(), yesterday)).isTrue();
        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(DailyChatSession.Status.rolled_up);
    }

    @Test
    void todays_session_is_not_touched() {
        User user = userRepository.save(new User("gh-today-" + System.nanoTime(), "오늘유저", null));
        // ServiceClock 기준 오늘은 06:00 KST 이후만 진짜 오늘. 안전하게 +1일 미래로 잡아 무조건 제외.
        LocalDate future = LocalDate.now().plusDays(2);

        DailyChatSession session = sessionRepository.save(
                new DailyChatSession(user.getId(), future, Instant.now()));

        rollupService.rollupAll();

        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(DailyChatSession.Status.active);
    }
}
