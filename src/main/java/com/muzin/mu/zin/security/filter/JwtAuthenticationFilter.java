package com.muzin.mu.zin.security.filter;

import com.muzin.mu.zin.entity.ArtistStatus;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.security.jwt.JwtUtils;
import com.muzin.mu.zin.security.model.PrincipalUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
* Filter vs OncePerRequestFilter
*
* Filter(implements)
* -> 서블릿 스펙 기본 인터페이스
* -> 스프링이든 뭐든 상관없이 톰캣이 실행해주는 가장 바닥 레벨 필터
*
* OncePerRequestFilter(extends)
* -> 스프링이 제공하는 추상 클래스
* -> 내부적으로 Filter를 구현
* -> "한 요청당 한 번만 실행되게" 도와주는 편의 클래스
*
* 토큰이 없거나 잘못된 경우 여기서 바로 막지 않고 익명으로 다음 필터에 넘김
* -> 공개 API는 그대로 동작, 인증이 필요한 API는 JsonSecurityErrorHandler 가 401/403 JSON 응답
*    (실패 사유는 AUTH_ERROR_ATTR 요청 속성으로 전달)
* */

@Slf4j
@Component // 스프링 빈으로 등록해서 securityFilterChain 설정 시 끼워넣을 수 있게 함
@RequiredArgsConstructor // final 필드들을 생성자로 자동 주입
public class JwtAuthenticationFilter extends OncePerRequestFilter { // 요청마다 한 번만 실행되는 필터

    public static final String AUTH_ERROR_ATTR = "muzin.authError";
    public static final String USER_ID_ATTR = "muzin.userId";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String TOKEN_INVALID = "TOKEN_INVALID";
    public static final String ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED";

    // 승인된 아티스트에게 추가로 부여하는 권한 (SecurityConfig 에서 아티스트 전용 경로에 사용)
    public static final String ARTIST_APPROVED_AUTHORITY = "ARTIST_APPROVED";

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 이미 인증된 경우 JWT 다시 검사 안하고 다음 필터로
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");

        //Bearer 토큰 아니면 패스 (익명 요청)
        if (!jwtUtils.isBearer(authorization)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 검증된 토큰 앞에 Bearer를 없앰(토큰만 분리)
        String accessToken = jwtUtils.removeBearer(authorization);

        try {
            // 서명 검증 + 만료 검사 + payload 파싱
            Claims claims = jwtUtils.getClaims(accessToken);
            Long userId = parseUserId(claims.getSubject());

            if (userId == null) {
                // 이메일 인증용 토큰 등 액세스 토큰이 아닌 경우
                request.setAttribute(AUTH_ERROR_ATTR, TOKEN_INVALID);
            } else {
                authenticate(request, userId);
            }
        } catch (ExpiredJwtException e) {
            request.setAttribute(AUTH_ERROR_ATTR, TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 검증 실패: {}", e.getMessage());
            request.setAttribute(AUTH_ERROR_ATTR, TOKEN_INVALID);
        }

        // 항상 다음 필터로 넘기기
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, Long userId) {
        // DB에서 유저 한 명 조회 - 매 요청마다 최신 상태(권한, 아티스트 승인, 정지 여부)를 반영
        Optional<User> optionalUser = userRepository.findWithRolesByUserId(userId);
        if (optionalUser.isEmpty()) {
            // 토큰 형식은 맞지만 DB에 해당 유저가 없을 때
            request.setAttribute(AUTH_ERROR_ATTR, TOKEN_INVALID);
            return;
        }

        User user = optionalUser.get();

        // 정지된 계정은 유효한 토큰이 있어도 인증하지 않음 (fail-closed)
        if (user.isSuspended()) {
            request.setAttribute(AUTH_ERROR_ATTR, ACCOUNT_SUSPENDED);
            return;
        }

        // 여기서 한 번 DB에서 roles 꺼내서 권한 리스트로 변환
        List<GrantedAuthority> authorities = new ArrayList<>();
        user.getUserRoles().forEach(userRole ->
                authorities.add(new SimpleGrantedAuthority(userRole.getRole().getRoleName())));

        // role만 보여줌 (프론트용)
        List<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        // 아티스트 승인은 Role이 아니라 상태값으로 관리되므로 권한으로 한 번 더 변환
        if (user.getArtistStatus() == ArtistStatus.APPROVED) {
            authorities.add(new SimpleGrantedAuthority(ARTIST_APPROVED_AUTHORITY));
        }

        // DB에서 가져온 User 엔티티를 Spring Security에서 쓰기 좋은 UserDetails 타입인 PrincipalUser로 변환
        PrincipalUser principalUser = PrincipalUser.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .password(user.getPassword())
                .username(user.getUsername())
                .profileImgUrl(user.getProfileImgUrl())
                .emailVerified(user.getEmailVerified())
                .artistStatus(user.getArtistStatus())
                .roles(roles)
                .authorities(authorities)
                .build();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principalUser, // principal
                null, // credentials (이미 JWT 검증 끝나서 null)
                authorities // 권한 리스트
        );

        // IP 같은 요청 관련 부가 정보를 authentication에 심어줌
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        // 이후 컨트롤러/서비스에서 @AuthenticationPrincipal 혹은 SecurityContextHolder로 꺼내서 사용 가능
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(USER_ID_ATTR, user.getUserId());
    }

    // 액세스 토큰의 subject는 userId(숫자 문자열). 숫자가 아니면 액세스 토큰이 아님
    private Long parseUserId(String subject) {
        if (subject == null) return null;
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
