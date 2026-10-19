package com.muzin.mu.zin.ai.tools;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 서비스 정책 안내 문서 (resources/ai/service-guide.md) - get_service_guide 도구의 유일한 근거
// "## topic: 제목" 단위로 나눠 둠. 문서에 없는 topic 은 "안내 없음"
@Component
public class ServiceGuide {

    private static final Pattern HEADER = Pattern.compile("^## ([a-z_]+): (.+)$");

    public record Section(String topic, String title, String body) { }

    private final Map<String, Section> sections;

    public ServiceGuide() {
        this.sections = Collections.unmodifiableMap(parse(load()));
    }

    public Map<String, Section> sections() {
        return sections;
    }

    private static String load() {
        try {
            return new ClassPathResource("ai/service-guide.md").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("ai/service-guide.md 를 읽지 못했습니다.", e);
        }
    }

    static Map<String, Section> parse(String text) {
        Map<String, Section> out = new LinkedHashMap<>();
        String topic = null, title = null;
        StringBuilder body = new StringBuilder();
        for (String line : text.split("\\R")) {
            Matcher m = HEADER.matcher(line);
            if (m.matches()) {
                if (topic != null) out.put(topic, new Section(topic, title, body.toString().trim()));
                topic = m.group(1);
                title = m.group(2).trim();
                body.setLength(0);
            } else if (topic != null) {
                body.append(line).append('\n');
            }
        }
        if (topic != null) out.put(topic, new Section(topic, title, body.toString().trim()));
        return out;
    }
}
