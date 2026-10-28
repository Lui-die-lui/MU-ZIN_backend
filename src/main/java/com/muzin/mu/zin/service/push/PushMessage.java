package com.muzin.mu.zin.service.push;

import java.util.regex.Pattern;

/*
 * 푸시 알림 내용 - 서비스 워커(public/sw.js)가 받아서 표시
 *  - 잠금화면에 보일 수 있으므로 상대 이름·메시지 내용은 넣지 않음
 *  - url 은 서비스 안 허용 경로만 (서비스 워커도 한 번 더 확인)
 */
public record PushMessage(String title, String body, String url, String tag) {

    // 서비스 워커의 ALLOWED 와 같은 규칙
    private static final Pattern ALLOWED_URL = Pattern.compile(
            "^/mypage/(chats(/\\d+)?|reservations(/requests)?)$");

    public PushMessage {
        if (url == null || !ALLOWED_URL.matcher(url).matches()) url = "/mypage";
    }

    public static PushMessage chat(long chatRoomId) {
        return new PushMessage("MU:ZIN", "새 메시지가 도착했어요", "/mypage/chats/" + chatRoomId, "chat-" + chatRoomId);
    }

    public String toJson() {
        return "{\"title\":\"" + esc(title) + "\",\"body\":\"" + esc(body) + "\",\"url\":\"" + esc(url)
                + "\",\"tag\":\"" + esc(tag) + "\"}";
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }
}
