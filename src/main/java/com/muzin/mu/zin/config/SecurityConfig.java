package com.muzin.mu.zin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration // 해당 클래스가 스프링 설정 파일인 점을 명시
@RequiredArgsConstructor // Lombok이 final 필드 + NonNull 필드에 대해 생성자 자동 생성
public class SecurityConfig {
}
