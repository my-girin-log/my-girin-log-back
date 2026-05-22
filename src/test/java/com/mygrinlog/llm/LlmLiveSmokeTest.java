package com.mygrinlog.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.mygrinlog.chat.ChatMessage;
import com.mygrinlog.chat.ReverseQuestionGenerator;
import com.mygrinlog.diary.DiaryDraft;
import com.mygrinlog.diary.DiaryGenerator;
import com.mygrinlog.persona.PersonaDraft;
import com.mygrinlog.persona.PersonaGenerator;
import com.mygrinlog.retrospective.Retrospective;
import com.mygrinlog.retrospective.RetrospectiveDraft;
import com.mygrinlog.retrospective.RetrospectiveGenerator;
import com.mygrinlog.diary.Diary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 실제 Claude API 를 호출하는 smoke test.
 *
 * 평소엔 비활성. 다음과 같이 환경변수 셋업해야 돌아간다:
 *   export ANTHROPIC_API_KEY=sk-ant-...
 *   export LLM_LIVE_TEST=1
 *   ./gradlew test --tests com.mygrinlog.llm.LlmLiveSmokeTest
 *
 * 비용 발생하니 평소엔 안 돌린다. 4 종 Generator 한 번씩 호출 (회고는 3-pass 라 ~4 호출).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "wootegotchi.llm.enabled=true")
@EnabledIfEnvironmentVariable(named = "LLM_LIVE_TEST", matches = "1")
class LlmLiveSmokeTest {

    @Autowired PersonaGenerator personaGenerator;
    @Autowired ReverseQuestionGenerator questionGenerator;
    @Autowired DiaryGenerator diaryGenerator;
    @Autowired RetrospectiveGenerator retroGenerator;

    private static final String SAMPLE_RAW_TEXT = """
            오늘 PR 머지함. 사실 별 거 아닌 변경이었는데 리뷰가 길어졌음.
            결국엔 컨벤션 얘기로 흘러갔고, 솔직히 좀 답답했음.
            근데 컨벤션 의견을 들어보니 일리 있긴 했음. 다음엔 미리 컨벤션 문서를 보고 PR 올려야겠다고 생각함.
            """;

    @Test
    void persona_then_question_then_diary_then_retrospective() {
        // 1) Persona
        PersonaDraft persona = personaGenerator.generate(List.of(), SAMPLE_RAW_TEXT);
        assertThat(persona.personaMd()).isNotBlank();
        assertThat(persona.analysis()).isNotNull();
        assertThat(persona.analysis().endingStyle()).isNotEmpty();

        // 우리 엔티티로 변환할 수 없으므로 Persona 객체 직접 구성하기엔 부담 — Generator 들이 Persona 엔티티를 받지만
        // 라이브 테스트에서는 PersonaDraft 만으로 검증. 실제 통합은 RollupService 통합 테스트에서.
        // 이 테스트의 목적은 4개 모델 호출이 살아있는지 확인하는 smoke 이므로 OK.

        com.mygrinlog.persona.Persona personaEntity = new com.mygrinlog.persona.Persona(
                999L, persona.personaMd(), persona.summary(), persona.sources(), persona.analysis());

        // 2) Reverse question
        List<ChatMessage> messages = List.of(
                new ChatMessage(1L, ChatMessage.Role.user,
                        "오늘 로또 미션에서 예외 처리 위치 한참 헤맸음. 결국 도메인 객체 생성자에서 던지기로 함.",
                        ChatMessage.Source.typed, Instant.now())
        );
        String question = questionGenerator.generate(messages, personaEntity);
        assertThat(question).isNotBlank();
        System.out.println("[live] question = " + question);

        // 3) Diary
        DiaryDraft diary = diaryGenerator.generate(LocalDate.now().minusDays(1), messages, personaEntity);
        assertThat(diary.title()).isNotBlank();
        assertThat(diary.markdown()).isNotBlank();
        assertThat(diary.emotionEmoji()).isNotBlank();
        System.out.println("[live] diary.title = " + diary.title());

        // 4) Retrospective (3-pass: analysis → writing → critique [→ refine])
        // 다이어리 엔티티 1건만 흉내
        Diary diaryEntity = new Diary(999L, LocalDate.now().minusDays(1), diary.title(),
                diary.rawText(), diary.markdown(), diary.emotionEmoji(), diary.tags());
        // diary 의 id 는 null 일 텐데 RetrospectiveGenerator 가 diaryEntity.getId() 를 포맷에 사용. null 허용.

        RetrospectiveDraft retro = retroGenerator.generate(
                Retrospective.Type.tech_blog,
                LocalDate.now().minusDays(7),
                LocalDate.now().minusDays(1),
                java.util.Map.of("focus", "트러블슈팅", "length", "short"),
                List.of(diaryEntity),
                personaEntity);
        assertThat(retro.markdown()).isNotBlank();
        assertThat(retro.title()).isNotBlank();
        System.out.println("[live] retro.title = " + retro.title());
    }
}
