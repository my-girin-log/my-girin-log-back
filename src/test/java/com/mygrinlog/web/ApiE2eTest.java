package com.mygrinlog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * /api/v1 전체 엔드포인트 한 흐름으로 검증.
 *  1) /auth/demo                            → token 발급
 *  2) /users/me                             → user/pet (calf/good 기본)
 *  3) /users/me?level=&condition=           → 응답 강제 변경 검증 (DB 미변경)
 *  4) /users/onboarding                     → Persona stub 응답 저장
 *  5) /chats/active + /chats/message        → 메시지 전송 + 역질문(stub) 받기
 *  6) /diaries/rollup                       → 정상 동작
 *  7) /diaries 월 조회 + /diaries/{dateKey} 404
 *  8) /retrospectives 빈 기간 → 400
 *  9) 인증 없으면 401
 *
 *  LLM 은 stub 모드 (LLM_ENABLED=false 기본). 실제 호출 없음.
 *  데이터 누적 회피를 위해 매 테스트 실행마다 unique githubId 사용.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiE2eTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private Long userId;
    private String token;
    private final String githubId = "e2e-" + System.nanoTime();

    @BeforeAll
    void loginOnce() throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "githubId": "%s", "nickname": "E2E", "avatarUrl": null }
                                """.formatted(githubId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(res.getResponse().getContentAsString());
        this.token = body.get("token").asText();
        this.userId = body.get("user").get("id").asLong();
        assertThat(token).isNotBlank();
        assertThat(userId).isPositive();
    }

    @Test @Order(1)
    void me_returns_user_and_pet_with_meta() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId))
                .andExpect(jsonPath("$.user.githubId").value(githubId))
                .andExpect(jsonPath("$.user.hasPersona").value(false))
                .andExpect(jsonPath("$.pet.level").value(0))
                .andExpect(jsonPath("$.pet.stage").value("calf"))
                .andExpect(jsonPath("$.pet.condition").value("good"))
                .andExpect(jsonPath("$.pet.levelUpExp").value(10))
                .andExpect(jsonPath("$.pet.meta.stateKey").value("1-calf-good"))
                .andExpect(jsonPath("$.pet.meta.stateNumber").value(1))
                .andExpect(jsonPath("$.pet.meta.totalFrames").value(4))
                .andExpect(jsonPath("$.pet.meta.spriteRowIndex").value(0));
    }

    @Test @Order(2)
    void me_with_overrides_returns_mocked_pet() throws Exception {
        mvc.perform(get("/api/v1/users/me?level=2&condition=terrible")
                        .header("X-Demo-User-Id", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pet.level").value(2))
                .andExpect(jsonPath("$.pet.stage").value("adult"))
                .andExpect(jsonPath("$.pet.condition").value("terrible"))
                .andExpect(jsonPath("$.pet.meta.stateKey").value("9-adult-terrible"))
                .andExpect(jsonPath("$.pet.meta.spriteRowIndex").value(8));
    }

    @Test @Order(3)
    void onboarding_creates_persona_with_stub() throws Exception {
        mvc.perform(post("/api/v1/users/onboarding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sources": [], "rawText": "오늘 PR 머지함. 솔직히 좀 답답했음." }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personaId").isNumber())
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.markdown").isNotEmpty());

        // hasPersona 가 true 로 바뀜
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.user.hasPersona").value(true));
    }

    @Test @Order(4)
    void chat_active_lazily_creates_session_and_message_flow_works() throws Exception {
        MvcResult activeRes = mvc.perform(get("/api/v1/chats/active")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.status").value("active"))
                .andExpect(jsonPath("$.messages").isArray())
                .andReturn();
        Long sessionId = mapper.readTree(activeRes.getResponse().getContentAsString())
                .get("session").get("id").asLong();

        mvc.perform(post("/api/v1/chats/message")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sessionId": %d, "content": "오늘 로또 미션에서 예외 처리 위치 한참 헤맸음" }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userMessage.role").value("user"))
                .andExpect(jsonPath("$.assistantMessage.role").value("assistant"))
                .andExpect(jsonPath("$.assistantMessage.content").isNotEmpty())
                .andExpect(jsonPath("$.petExpGained").value(2))
                .andExpect(jsonPath("$.currentPetExp").isNumber())
                .andExpect(jsonPath("$.pet.meta.stateKey").exists());
    }

    @Test @Order(5)
    void rollup_runs_and_returns_pet_update() throws Exception {
        mvc.perform(post("/api/v1/diaries/rollup")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.petUpdate.level").exists())
                .andExpect(jsonPath("$.petUpdate.meta.stateKey").exists())
                .andExpect(jsonPath("$.batchResult.processedSessions").exists());
    }

    @Test @Order(6)
    void diary_list_empty_for_current_month_and_detail_returns_404() throws Exception {
        String ym = YearMonth.now().toString();
        mvc.perform(get("/api/v1/diaries?yearMonth=" + ym)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaries").isArray());

        // 빈 날짜 상세 → 404 (스펙 §6 워넬 합의)
        mvc.perform(get("/api/v1/diaries/" + LocalDate.now())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test @Order(7)
    void retrospective_create_fails_when_no_diaries_in_range() throws Exception {
        mvc.perform(post("/api/v1/retrospectives")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "tech_blog",
                                  "rangeStartDate": "2026-04-01",
                                  "rangeEndDate":   "2026-04-30",
                                  "promptOptions":  { "length": "short" }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test @Order(8)
    void unauthenticated_request_returns_401() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
