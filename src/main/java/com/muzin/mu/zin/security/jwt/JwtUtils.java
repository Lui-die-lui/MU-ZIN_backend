package com.muzin.mu.zin.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.Key;
import java.time.Duration;
import java.util.Date;

@Component // JWT 관련 기능들을 모아둔 도구 클래스(JwtUtils)를 스프링 컨테이너에 등록해둔 상태
public class JwtUtils {

    // JWT 서명 시 사용되는 비밀 키
    // final이라서 생성자에서 한 번 넣고나면 절대 안바뀜
    private final SecretKey KEY;

    // Key = 공개키/개인키, 대칭키 등 모든 종류의 키를 다 포괄하는 상위 타입
    // SecretKey = 대칭키 전용 타입 - 서명 검증에 쓸 키를 명시적으로 지정할 수 있게 됨



    // application.properties 의 값 + 비밀키
    public JwtUtils(@Value("${jwt.secret}") String secret) {
        KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)); //BASE64 인코딩 문자열을 바이트 배열로 변환(디코딩)
    }

    // userId 기반 AccessToken
    public String generateAccessToken(Long userId) {
        return Jwts.builder()
                .subject(userId.toString()) // sub = userId
//                .claim("email", email) // 나중에 userId를 넣어서 claims.getId()
                .expiration(new Date(new Date().getTime() + (1000L * 60L * 60L * 24L * 30L)))
                .signWith(KEY) // 서명 생성
                .compact(); // 문자 직렬화
    }

    // 이메일 인증 링크 유효시간 = 인증 메일 재발송 대기시간 (MailService 와 공유)
    public static final Duration VERIFY_TOKEN_TTL = Duration.ofMinutes(3);

    // 이메일 인증용
    public String generateVerifyToken(String id) {
        return Jwts.builder()
                .subject("VerifyToken") // SMTP 인증용 토큰
                .id(id)
                .expiration(new Date(System.currentTimeMillis() + VERIFY_TOKEN_TTL.toMillis()))
                .signWith(KEY)
                .compact();
    }

    // 소셜 첫 가입: 동의를 받기 전까지 회원을 만들지 않고 제공자 정보만 담아 두는 임시 토큰 (10분)
    //  - subject 가 숫자가 아니라서 로그인 토큰으로는 쓸 수 없음 (JwtAuthenticationFilter 가 거부)
    public static final Duration OAUTH2_SIGNUP_TTL = Duration.ofMinutes(10);
    private static final String OAUTH2_SIGNUP_SUBJECT = "OAuth2Signup";

    public record OAuth2Signup(String provider, String providerUserId, String email, boolean emailVerified) { }

    public String generateOAuth2SignupToken(OAuth2Signup s) {
        return Jwts.builder()
                .subject(OAUTH2_SIGNUP_SUBJECT)
                .claim("provider", s.provider())
                .claim("providerUserId", s.providerUserId())
                .claim("email", s.email())
                .claim("emailVerified", s.emailVerified())
                .expiration(new Date(System.currentTimeMillis() + OAUTH2_SIGNUP_TTL.toMillis()))
                .signWith(KEY)
                .compact();
    }

    // 만료·위조·다른 종류 토큰이면 JwtException
    public OAuth2Signup parseOAuth2SignupToken(String token) {
        Claims c = getClaims(token);
        if (!OAUTH2_SIGNUP_SUBJECT.equals(c.getSubject())) throw new MalformedJwtException("가입 토큰이 아님");
        return new OAuth2Signup(c.get("provider", String.class), c.get("providerUserId", String.class),
                c.get("email", String.class), Boolean.TRUE.equals(c.get("emailVerified", Boolean.class)));
    }

    public boolean isBearer(String token) {
        if (token == null) {
            return false; // 토큰 없으면 false
        }
        if (!token.startsWith("Bearer ")) {
            return false; // Bearer로 시작 안하면 false
        }
        return true;
    }

    public String removeBearer(String token) {
        return token.replaceFirst("Bearer ", "");
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(KEY) // 해당 키로 서명 검증
                .build()
                .parseSignedClaims(token)
                .getPayload(); // 그 안의 Claim만 꺼내기
    }
}
