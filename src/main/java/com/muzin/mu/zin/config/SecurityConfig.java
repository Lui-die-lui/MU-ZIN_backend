package com.muzin.mu.zin.config;

import com.muzin.mu.zin.security.filter.JwtAuthenticationFilter;
import com.muzin.mu.zin.security.handler.JsonSecurityErrorHandler;
import com.muzin.mu.zin.security.handler.OAuth2FailureHandler;
import com.muzin.mu.zin.security.handler.OAuth2SuccessHandler;
import com.muzin.mu.zin.service.OAuth2PrincipalUserService;
import com.muzin.mu.zin.security.filter.RequestIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration // 해당 클래스가 스프링 설정 파일인 점을 명시
@EnableMethodSecurity
//@RequiredArgsConstructor // Lombok이 final 필드 + NonNull 필드에 대해 생성자 자동 생성
public class SecurityConfig {

    // 의존성 안정적으로 유지
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonSecurityErrorHandler jsonSecurityErrorHandler;
//    private final OAuth2PrincipalUserService oAuth2PrincipalUserService;
//    private final OAuth2FailureHandler oAuth2FailureHandler;
//    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    // 스프링 컨테이너에 해당 타입 빈을 등록
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 타입을 인터페이스 passwordEncoder로 맞춰두면
    // 나중에 다른 인코더로 바꾸고 싶을 때 Config만 수정하면 끝이라 더욱 유연(실무 사용)
    //    public BCryptPasswordEncoder bCryptPasswordEncoder() {
    //        return new BCryptPasswordEncoder();
    //    }

//    // 생성자 주입
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JsonSecurityErrorHandler jsonSecurityErrorHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jsonSecurityErrorHandler = jsonSecurityErrorHandler;
    }

    // CORS 설정 만드는 부분 - 어떤 프론트엔드에서 이 백엔드를 호출할 수 있나?
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.frontend-url}") String frontendUrl) {
        // CORS 설정 객체 생성
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        // 허용할 Origin 패턴 설정
//        // 나중에 실제 도메인만 열어주는 식으로 사용하면 됨
//        corsConfiguration.addAllowedOriginPattern(CorsConfiguration.ALL);
        corsConfiguration.addAllowedOriginPattern(frontendUrl);
        corsConfiguration.addAllowedOriginPattern("http://127.0.0.1:5173"); // (선택) 로컬 IP로 접속할 때 대비
        // 프론트에서 요청 ID를 읽을 수 있게 노출
        corsConfiguration.addExposedHeader(RequestIdFilter.HEADER);
        // 허용 헤더 설정
        corsConfiguration.addAllowedHeader(CorsConfiguration.ALL);
        // 쿠키 / 인증정보 포함한 요청 허용
        corsConfiguration.setAllowCredentials(true);
        // GET, POST, PUT, DELETE 등 모두 허용
        corsConfiguration.addAllowedMethod(CorsConfiguration.ALL);

        // "/**" 모든 경로에 이 CORS 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);

        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   OAuth2PrincipalUserService oAuth2PrincipalUserService,
                                                   OAuth2SuccessHandler oAuth2SuccessHandler,
                                                   OAuth2FailureHandler oAuth2FailureHandler) throws Exception { // 순환참조 오류나서 이쪽으로...
        // 세션/폼 로그인 안쓰고 JWT + REST API로만 갈거라면 OK
        http.cors(Customizer.withDefaults());
        http.csrf(csrf -> csrf.disable());
        http.formLogin(formlogin -> formlogin.disable());
        http.httpBasic(httpBasic ->httpBasic.disable());
        http.logout(logout -> logout.disable());

        http.sessionManagement(session
                -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // 인증/인가 실패도 GlobalExceptionHandler 와 같은 JSON 형태로 (OAuth2 로그인 페이지 302 리다이렉트 방지)
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(jsonSecurityErrorHandler)
                .accessDeniedHandler(jsonSecurityErrorHandler)
        );

        http.authorizeHttpRequests(auth -> {auth

                // 아티스트 레슨/슬롯/반복규칙 관리, 받은 예약 관리 - 승인된 아티스트만
                // (아티스트 승인은 Role 이 아닌 users.artist_status 라서 JWT 필터가 ARTIST_APPROVED 권한으로 변환)
                .requestMatchers("/lessons/me", "/lessons/me/**").hasAuthority(JwtAuthenticationFilter.ARTIST_APPROVED_AUTHORITY)
                .requestMatchers("/reservations/artist", "/reservations/artist/**").hasAuthority(JwtAuthenticationFilter.ARTIST_APPROVED_AUTHORITY)

                // 레슨 검색/상세/예약 가능 시간 보기는 아무나 접근 가능
                .requestMatchers(HttpMethod.GET, "/lessons/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/regions/**", "/instruments/**", "/artists/**",
                        "/reviews/keywords", "/reviews/lessons/**", "/reviews/artists/**").permitAll()

                .requestMatchers("/auth/signup", "/auth/signin", "/auth/principal", "/error",
                        "/oauth2/**", "/login/oauth2/**", "/mail/verify").permitAll();

            auth.anyRequest().authenticated();
        });

        http.oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2PrincipalUserService))
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
        );

        return http.build();
    }
}
