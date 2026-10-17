package com.muzin.mu.zin.security.handler;

import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.security.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/*
 * 시큐리티 필터 단계에서 막힌 요청도 GlobalExceptionHandler 와 같은 JSON 형태로 응답
 *  - 미인증(토큰 없음/만료/위조) -> 401
 *  - 정지된 계정 -> 403 ACCOUNT_SUSPENDED
 *  - 권한 부족(관리자, 승인 아티스트 전용 경로) -> 403
 * 기본 동작이던 OAuth2 로그인 페이지 302 리다이렉트를 대체함
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public JsonSecurityErrorHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
        resolver.resolveException(request, response, null, authFailure(request));
    }

    // JWT 필터가 남긴 실패 사유(정지/만료/위조)를 응답 예외로 변환
    // 공개 경로(/auth/principal 등)에서 로그인 상태를 확인할 때도 같은 사유를 쓰도록 static 으로 공개
    public static ApiException authFailure(HttpServletRequest request) {
        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTR);

        if (JwtAuthenticationFilter.ACCOUNT_SUSPENDED.equals(reason)) {
            return ApiException.forbidden("ACCOUNT_SUSPENDED", "이용이 정지된 계정입니다. 관리자에게 문의해주세요.");
        }
        if (JwtAuthenticationFilter.TOKEN_EXPIRED.equals(reason)) {
            return ApiException.unauthorized("TOKEN_EXPIRED", "로그인이 만료되었습니다. 다시 로그인해주세요.");
        }
        if (JwtAuthenticationFilter.TOKEN_INVALID.equals(reason)) {
            return ApiException.unauthorized("TOKEN_INVALID", "로그인 정보가 올바르지 않습니다. 다시 로그인해주세요.");
        }
        return ApiException.unauthorized("UNAUTHORIZED", "로그인이 필요합니다.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) {
        String uri = request.getRequestURI();
        ApiException ex;
        if (uri.startsWith("/lessons/me") || uri.startsWith("/reservations/artist")) {
            ex = ApiException.forbidden("ARTIST_APPROVAL_REQUIRED", "승인된 아티스트만 이용할 수 있습니다.");
        } else {
            ex = ApiException.forbidden("FORBIDDEN", "접근 권한이 없습니다.");
        }
        resolver.resolveException(request, response, null, ex);
    }
}
