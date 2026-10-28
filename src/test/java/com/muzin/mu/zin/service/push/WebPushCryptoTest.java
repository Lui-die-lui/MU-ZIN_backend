package com.muzin.mu.zin.service.push;

import com.muzin.mu.zin.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushCryptoTest {

    private static byte[] d(String s) {
        return WebPushCrypto.b64d(s.replace(" ", ""));
    }

    @Test
    @DisplayName("RFC 8291 부록 A 의 예시 값과 암호화 결과가 같다")
    void rfc8291Vector() throws Exception {
        byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.US_ASCII);
        byte[] asPublic = d("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        byte[] asPrivate = d("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
        byte[] uaPublic = d("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] salt = d("DGv6ra1nlYgDCS1FRnbzlw");
        byte[] auth = d("BTBZMqHH6r4Tts7J_aSIgg");

        KeyPair as = new KeyPair(WebPushCrypto.publicKey(asPublic), WebPushCrypto.privateKey(asPrivate));
        byte[] out = WebPushCrypto.encrypt(plaintext, uaPublic, auth, as, salt);

        String header = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
        String ciphertext = "8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ";
        byte[] expected = concat(d(header), d(ciphertext));
        assertThat(WebPushCrypto.b64(out)).isEqualTo(WebPushCrypto.b64(expected));
    }

    @Test
    @DisplayName("VAPID JWT 는 ES256(r||s 64바이트) 서명으로 공개키 검증이 된다")
    void vapidSignatureVerifies() throws Exception {
        KeyPair kp = WebPushCrypto.newKeyPair();
        byte[] pub = WebPushCrypto.encode((java.security.interfaces.ECPublicKey) kp.getPublic());
        String header = WebPushCrypto.vapidAuthorization("https://fcm.googleapis.com", "mailto:a@b.c", 1_900_000_000L,
                (java.security.interfaces.ECPrivateKey) kp.getPrivate(), pub);

        assertThat(header).startsWith("vapid t=").contains(", k=" + WebPushCrypto.b64(pub));
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).isEqualTo("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1900000000,\"sub\":\"mailto:a@b.c\"}");

        byte[] sig = Base64.getUrlDecoder().decode(parts[2]);
        assertThat(sig).hasSize(64);
        Signature v = Signature.getInstance("SHA256withECDSAinP1363Format");
        v.initVerify(WebPushCrypto.publicKey(pub));
        v.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(v.verify(sig)).isTrue();
    }

    @Test
    @DisplayName("구독 주소는 알려진 푸시 서버의 https 주소만 허용")
    void endpointAllowList() {
        assertThat(PushService.validEndpoint("https://fcm.googleapis.com/fcm/send/abc")).startsWith("https://fcm.googleapis.com/");
        assertThat(PushService.validEndpoint("https://web.push.apple.com/QGx")).startsWith("https://web.push.apple.com/");
        assertThat(PushService.validEndpoint("https://updates.push.services.mozilla.com/wpush/v2/x")).isNotBlank();
        assertThat(PushService.validEndpoint("https://wns2-db5p.notify.windows.com/w/?token=x")).isNotBlank();

        for (String bad : new String[]{"http://fcm.googleapis.com/fcm/send/abc", "https://evil.com/fcm.googleapis.com",
                "https://fcm.googleapis.com.evil.com/x", "https://localhost/x", "https://fcm.googleapis.com:8443/x",
                "https://user@fcm.googleapis.com/x", "not a url"}) {
            assertThatThrownBy(() -> PushService.validEndpoint(bad)).as(bad).isInstanceOf(ApiException.class);
        }
    }

    @Test
    @DisplayName("알림 이동 주소는 허용 경로만, 아니면 마이페이지로")
    void pushMessageUrl() {
        assertThat(PushMessage.chat(12).url()).isEqualTo("/mypage/chats/12");
        assertThat(new PushMessage("t", "b", "/mypage/reservations/requests", "x").url()).isEqualTo("/mypage/reservations/requests");
        assertThat(new PushMessage("t", "b", "https://evil.com", "x").url()).isEqualTo("/mypage");
        assertThat(new PushMessage("t", "b", "//evil.com/mypage/chats/1", "x").url()).isEqualTo("/mypage");
        assertThat(PushMessage.chat(3).toJson())
                .isEqualTo("{\"title\":\"MU:ZIN\",\"body\":\"새 메시지가 도착했어요\",\"url\":\"/mypage/chats/3\",\"tag\":\"chat-3\"}");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] o = new byte[a.length + b.length];
        System.arraycopy(a, 0, o, 0, a.length);
        System.arraycopy(b, 0, o, a.length, b.length);
        return o;
    }
}
