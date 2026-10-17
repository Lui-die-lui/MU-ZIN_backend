package com.muzin.mu.zin.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/*
 * 요청마다 서버가 만든 ID를 붙여 로그와 응답을 연결
 *  - 응답 헤더 X-Request-Id (브라우저가 보낸 값은 쓰지 않음)
 *  - 로그 패턴의 %X{requestId}
 *  - 요청 1건당 접근 로그 1줄: 메서드, 경로, 상태, 소요시간, 사용자 지문(userId 해시 앞 12자리)
 *    본문/토큰/쿼리스트링은 남기지 않음
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        MDC.put(MDC_KEY, requestId.substring(0, 8));
        response.setHeader(HEADER, requestId);
        String userFingerprint = "-";
        try {
            chain.doFilter(request, response);
        } finally {
            // 시큐리티 컨텍스트는 이 시점에 이미 비워져 있으므로 JWT 필터가 남긴 요청 속성을 사용
            if (request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR) instanceof Long userId) {
                userFingerprint = fingerprint(userId);
            }
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (!request.getRequestURI().startsWith("/error")) {
                log.info("access {} {} -> {} ({}ms) user={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), ms, userFingerprint);
            }
            MDC.remove(MDC_KEY);
        }
    }

    private static String fingerprint(Long userId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(("muzin-user:" + userId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            return "-";
        }
    }
}
