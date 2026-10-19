package com.muzin.mu.zin.ai.tools;

import java.util.regex.Pattern;

// AI 와 주고받는 글 정리
// - clean: 제어 문자 제거 + 길이 제한 (도구 결과에 넣는 DB 글, 모델이 쓴 추천 이유)
// - sanitizeModelText: 모델이 쓴 답변에서 URL·금액을 빼고 화면에는 서버가 만든 카드 값만 보이게
public final class AiText {
    private AiText() {}

    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    // 숫자 바로 뒤의 "원"/"만 원"만 (예: 30,000원이고, 5만 원) - "원하는"처럼 숫자가 앞에 없는 말은 건드리지 않음
    private static final Pattern WON = Pattern.compile("(?:\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?)\\s*(?:만\\s*)?원");

    public static String clean(String text, int max) {
        if (text == null) return null;
        String s = text.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").replaceAll("[ \\t]{2,}", " ").trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    public static String sanitizeModelText(String text, int max) {
        if (text == null) return "";
        String s = URL.matcher(text).replaceAll("(링크는 아래 카드에서 확인해 주세요)");
        s = WON.matcher(s).replaceAll("(가격은 아래 카드에서 확인해 주세요)");
        String c = clean(s, max);
        return c == null ? "" : c;
    }
}
