package com.muzin.mu.zin.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.Key;
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

    // 이거는 그대로 (이메일 인증용)
    public String generateVerifyToken(String id) {
        return Jwts.builder()
                .subject("VerifyToken") // SMTP 인증용 토큰
                .id(id)
                .expiration(new Date(new Date().getTime() + (1000L * 60L * 3L)))
                .signWith(KEY)
                .compact();
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
