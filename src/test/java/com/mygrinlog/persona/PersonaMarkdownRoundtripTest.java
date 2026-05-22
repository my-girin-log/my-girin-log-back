package com.mygrinlog.persona;

import static org.assertj.core.api.Assertions.assertThat;

import com.mygrinlog.user.User;
import com.mygrinlog.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한국어 markdown 이 DB 왕복 후 손상 없이 보존되는지 확인.
 * /users/onboarding 응답에서 persona markdown 일부가 잘리는 버그 재현 → 수정 검증.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersonaMarkdownRoundtripTest {

    @Autowired UserRepository userRepository;
    @Autowired PersonaRepository personaRepository;
    @Autowired EntityManager em;

    @Test
    void persona_markdown_roundtrip_preserves_full_text() throws Exception {
        String original = new String(new ClassPathResource("prompts/persona-fallback.md")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        User user = userRepository.save(new User("md-test-" + System.nanoTime(), "md", null));
        Persona persona = new Persona(user.getId(), original, "summary",
                List.of("src1"), PersonaAnalysis.fallback());
        Persona saved = personaRepository.save(persona);

        em.flush();
        em.clear();  // 1차 캐시 비워 강제로 DB 에서 다시 읽기

        Persona reloaded = personaRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getMarkdown())
                .as("DB 왕복 후 markdown 보존")
                .isEqualTo(original);
    }
}
