package com.muzin.mu.zin.security.filter;

import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.security.jwt.JwtUtils;
import com.muzin.mu.zin.security.model.PrincipalUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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
* */

@Slf4j
@Component // 스프링 빈으로 등록해서 securityFilterChain 설정 시 끼워넣을 수 있게 함
@RequiredArgsConstructor // final 필드들을 생성자로 자동 주입
public class JwtAuthnticationFilter extends OncePerRequestFilter { // 요청마다 한 번만 실행되는 필터

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    // OncePerRequestFilter가 제공하는 추상 메서드
    // 모든 HTTP 요청이 들어올 때마다 여기로  한 번씩 들어온다고 보면 됨
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

        // 요청 헤더의 Authorization을 가져옴
        String authorization = request.getHeader("Authorization");
        log.debug("Authorization header = {}", authorization); // 이런게 있음?

        //Bearer 토큰 아니면 패스
        if (!jwtUtils.isBearer(authorization)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 검증된 토큰 앞에 Bearer를 없앰(토큰만 분리)
        String accessToken = jwtUtils.removeBearer(authorization);

        try {
            // JJWT 라이브러리 같은 걸로 서명 검증 + 만료 검사 + payload 파싱해서 Claims 객체 가져옴
            Claims claims = jwtUtils.getClaims(accessToken);

            String subject = claims.getSubject();

            log.debug("JWT subject = {}", subject);
            if (subject == null) {
                throw new AuthenticationServiceException("인증 실패 : 토큰에 userId가 없습니다.");
            }

            // 해당 로직때문에 계속 잘못 해석되고 있었음
            Integer userId = Integer.parseInt(subject);

            // DB에서 유저 한 명 조회(없으면 Optional.empty())
            Optional<User> optionalUser = userRepository.findWithRolesByUserId(userId);

            // ifPresentOrElse 두 개의 인수가 필요함
            // 유저가 존재하면 -> 람다 첫 번째 블록 실행
            // 없으면 -> 두번째 블록(예외 던지는 부분) 실행
            optionalUser.ifPresentOrElse(user -> {

                // 여기서 한 번 DB에서 roles 꺼내서 권한 리스트로 변환
                List<SimpleGrantedAuthority> authorities = user.getUserRoles().stream()
                        .map(userRole -> new SimpleGrantedAuthority(userRole.getRole().getRoleName()))
                        .toList();

                // role만 보여줌
                List<String> roles = authorities.stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList();

                // DB에서 가져온 User 엔티티를 Spring Security에서
                // 쓰기 좋은 UserDetails 타입인 PrincipalUser로 변환
                PrincipalUser principalUser = PrincipalUser.builder()
                        .userId(user.getUserId())
                        .email(user.getEmail())
                        .password(user.getPassword())
                        .username(user.getUsername())
                        .profileImgUrl(user.getProfileImgUrl())
                        .emailVerified(user.getEmailVerified())
                        .artistStatus(user.getArtistStatus())
                        .roles(roles)
                        .build();

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principalUser, // principal
                        null, // credentials (보통 패스워드인데 이미 JWT 검증 끝나서 null)
                        principalUser.getAuthorities() // 권한 리스트
                );

                // IP, 세션 ID 같은 요청 관련 부가 정보를 authentication에 심어줌
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // 현재 요청 스레드의 SecurityContext에 인증 정보 저장
                // 이후 컨트롤러 서비스에서 @AuthenticationPrincipal 혹은 SecurityContextHolder로 꺼내서 사용 가능
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }, () -> {
                // 토큰 형식은 맞지만 DB에 해당 유저가 없을 때
                throw new AuthenticationServiceException("인증 실패 : 사용자가 없습니다.");
            });

        } catch (RuntimeException e) {
            log.error("JWT 인증 실패", e);
        }
        // 항상 다음 필터로 넘기기
        filterChain.doFilter(request, response);
    }
}
