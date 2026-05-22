package com.mygrinlog.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * 인-메모리 Bearer 토큰 저장소. 데모 전용.
 *  - 프로세스 재시작 시 모든 토큰 무효.
 *  - 만료/회수 정책 없음.
 *  실제 OAuth/JWT 도입은 본 모듈 범위 밖 (보안 강화 시 별도 모듈).
 */
@Component
public class TokenStore {

    private final ConcurrentMap<String, Long> tokenToUserId = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String issue(Long userId) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenToUserId.put(token, userId);
        return token;
    }

    public Optional<Long> resolve(String token) {
        if (token == null) return Optional.empty();
        Long id = tokenToUserId.get(token);
        return Optional.ofNullable(id);
    }

    public void revoke(String token) {
        if (token != null) tokenToUserId.remove(token);
    }
}
