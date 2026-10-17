package com.muzin.mu.zin.service;

import com.muzin.mu.zin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

// 가입 시 기본 유저명 생성
// 예전엔 username = email 이라서 아티스트 카드/레슨 상세 등 공개 화면에 이메일이 그대로 노출됐음
// -> 이메일 앞부분 + 랜덤 4자리 (예: muzin_a3f9). 사용자가 마이페이지에서 변경 가능
@Component
@RequiredArgsConstructor
public class UsernameGenerator {

    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;

    public String generate(String email) {
        String local = email == null ? "" : email.split("@")[0];
        // 영문/숫자/밑줄/점만 남기고 최대 20자
        String base = local.replaceAll("[^A-Za-z0-9_.]", "");
        if (base.isBlank()) base = "user";
        if (base.length() > 20) base = base.substring(0, 20);

        for (int i = 0; i < 10; i++) {
            String candidate = base + "_" + randomSuffix();
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("유저명 생성에 실패했습니다. 다시 시도해주세요.");
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
