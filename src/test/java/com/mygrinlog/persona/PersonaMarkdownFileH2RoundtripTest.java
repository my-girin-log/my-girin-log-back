package com.mygrinlog.persona;

import static org.assertj.core.api.Assertions.assertThat;

import com.mygrinlog.user.User;
import com.mygrinlog.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 환경(dev/file-H2) 의 markdown 잘림 버그 재현 + 회귀 방지.
 * @Lob + file-mode H2(MVStore) + 멀티바이트 UTF-8 조합에서 일부 청크가 사라지는 이슈가 있었다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersonaMarkdownFileH2RoundtripTest {

    @DynamicPropertySource
    static void useFileH2(DynamicPropertyRegistry registry) throws Exception {
        Path tmp = Files.createTempDirectory("h2-roundtrip-test");
        // file 모드 + MODE=MySQL — dev 프로필과 동일한 셋업
        String url = "jdbc:h2:file:" + tmp.resolve("mygrinlog-roundtrip").toAbsolutePath()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired UserRepository userRepository;
    @Autowired PersonaRepository personaRepository;
    @Autowired EntityManager em;

    @Test
    void persona_markdown_roundtrip_preserves_full_text_on_file_mode_h2() throws Exception {
        String original = new String(new ClassPathResource("prompts/persona-fallback.md")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        User user = userRepository.save(new User("md-file-" + System.nanoTime(), "md", null));
        Persona persona = new Persona(user.getId(), original, "summary",
                List.of("src1"), PersonaAnalysis.fallback());
        Persona saved = personaRepository.save(persona);

        em.flush();
        em.clear();

        Persona reloaded = personaRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getMarkdown())
                .as("file-mode H2 왕복 후 markdown 보존")
                .isEqualTo(original);
    }
}
