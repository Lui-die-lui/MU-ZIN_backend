package com.muzin.mu.zin.security.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// OAuth2 로그인 도중 에러가 나면 프론트의 에러페이지로 redirect 시키고 errorCode=OAUTH2_FAILED 붙여보냄
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("{oauth2.redirect.error-url}") // application.properties 에 있는 값을 받아옴
    private String errorRedirectUrl;

    // 살패시 자동 호출
    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, SecurityException {
        // URL에 쿼리 파라미터 붙일 때 문자열 더하기보다 안전, 인코딩도 깔끔
       String targetUrl = UriComponentsBuilder.fromUriString(errorRedirectUrl)
               .queryParam("errorCode", "OAUTH2_FAILED") // ?errorCode=OAUTH2_FAILED 붙음
               .build()
               .encode(StandardCharsets.UTF_8) // URL 인코딩 깨지지 않게
               .toString();

       getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

}
