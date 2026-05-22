package com.mygrinlog.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wootegotchi.auth")
public record AuthProperties(
        boolean allowDemoHeader,
        String githubAuthorizeUrl,
        String githubClientId,
        String githubRedirectUri
) {}
