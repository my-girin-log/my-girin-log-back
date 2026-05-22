package com.mygrinlog.auth;

import com.mygrinlog.common.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @CurrentUser Long userId 파라미터를 Authorization: Bearer 헤더 또는 X-Demo-User-Id 헤더에서 해석.
 *
 *  데모 헤더 X-Demo-User-Id 는 인증 없이 곧바로 유저를 가장 — 통합 테스트와 curl 데모용.
 *  운영 모드에서는 application.yml 의 wootegotchi.auth.allowDemoHeader=false 로 끄면 됨.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final TokenStore tokenStore;
    private final boolean allowDemoHeader;

    public CurrentUserArgumentResolver(TokenStore tokenStore, AuthProperties authProperties) {
        this.tokenStore = tokenStore;
        this.allowDemoHeader = authProperties.allowDemoHeader();
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().equals(Long.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new UnauthorizedException("No HTTP request");
        }

        if (allowDemoHeader) {
            String demo = request.getHeader("X-Demo-User-Id");
            if (demo != null && !demo.isBlank()) {
                try { return Long.parseLong(demo.trim()); }
                catch (NumberFormatException e) { throw new UnauthorizedException("Invalid X-Demo-User-Id"); }
            }
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing Bearer token");
        }
        String token = header.substring("Bearer ".length()).trim();
        return tokenStore.resolve(token).orElseThrow(() -> new UnauthorizedException("Invalid token"));
    }
}
