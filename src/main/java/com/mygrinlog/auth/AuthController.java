package com.mygrinlog.auth;

import com.mygrinlog.common.web.ApiPaths;
import com.mygrinlog.pet.PetStateService;
import com.mygrinlog.user.User;
import com.mygrinlog.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * GitHub OAuth (스펙 §3.1) — 본 모듈은 데모 친화적 stub:
 *  - GET  /auth/github               : 실제 GitHub authorize URL 로 302 (없으면 데모 URL).
 *  - GET  /auth/github/callback      : ?code=&state= 받아 (실 교환은 미구현) 데모 유저 생성/조회 + 토큰 발급.
 *  - POST /auth/demo                 : { "githubId": "..." } 받아 즉시 토큰 발급. 통합 테스트와 curl 데모용.
 *
 *  운영 도입 시 callback 안에서 GitHub access_token 교환 + /user 조회로 githubId 받아오는 부분만 채우면 됨.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/auth")
public class AuthController {

    private final TokenStore tokenStore;
    private final UserRepository userRepository;
    private final PetStateService petStateService;
    private final AuthProperties properties;

    public AuthController(TokenStore tokenStore, UserRepository userRepository,
                          PetStateService petStateService, AuthProperties properties) {
        this.tokenStore = tokenStore;
        this.userRepository = userRepository;
        this.petStateService = petStateService;
        this.properties = properties;
    }

    @GetMapping("/github")
    public void startGithubOAuth(HttpServletResponse response) throws IOException {
        String base = properties.githubAuthorizeUrl();
        if (base == null || base.isBlank()) {
            // 데모: GitHub 앱 미등록 상태에서도 흐름은 보여주기 위한 placeholder.
            response.sendRedirect("/api/v1/auth/github/callback?code=DEMO_CODE&state=demo");
            return;
        }
        String redirect = "%s?client_id=%s&redirect_uri=%s&scope=read:user".formatted(
                base,
                URLEncoder.encode(properties.githubClientId(), StandardCharsets.UTF_8),
                URLEncoder.encode(properties.githubRedirectUri(), StandardCharsets.UTF_8)
        );
        response.sendRedirect(redirect);
    }

    @GetMapping("/github/callback")
    @Transactional
    public AuthResult githubCallback(@RequestParam(required = false) String code,
                                     @RequestParam(required = false) String state) {
        // TODO 운영 도입 시: code → POST https://github.com/login/oauth/access_token
        //                  → GET https://api.github.com/user 로 실제 githubId 획득.
        String githubId = "demo-" + (code == null ? "anon" : code);
        User user = upsertUser(githubId, "데모유저-" + githubId, null);
        return new AuthResult(tokenStore.issue(user.getId()), toUserView(user));
    }

    @PostMapping("/demo")
    @Transactional
    public AuthResult demoLogin(@RequestBody DemoLoginRequest request) {
        if (request == null || request.githubId() == null || request.githubId().isBlank()) {
            throw new IllegalArgumentException("githubId is required");
        }
        User user = upsertUser(
                request.githubId(),
                request.nickname() == null ? request.githubId() : request.nickname(),
                request.avatarUrl()
        );
        return new AuthResult(tokenStore.issue(user.getId()), toUserView(user));
    }

    private User upsertUser(String githubId, String nickname, String avatarUrl) {
        return userRepository.findByGithubId(githubId)
                .orElseGet(() -> {
                    User saved = userRepository.save(new User(githubId, nickname, avatarUrl));
                    petStateService.getOrCreate(saved.getId());
                    return saved;
                });
    }

    private UserView toUserView(User user) {
        boolean hasPersona = false; // /users/me 에서 실제 조회. /auth 응답에서는 false 기본.
        return new UserView(user.getId(), user.getGithubId(), user.getNickname(),
                user.getAvatarUrl(), hasPersona, user.getCreatedAt());
    }

    public record DemoLoginRequest(String githubId, String nickname, String avatarUrl) {}

    public record AuthResult(String token, UserView user) {}

    public record UserView(Long id, String githubId, String nickname, String avatarUrl,
                           boolean hasPersona, java.time.Instant createdAt) {}
}
