package com.muzin.mu.zin.service.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/*
 * 표준 웹 푸시 암호화 · 서명 (JDK 기본 암호화만 사용, 외부 라이브러리 없음)
 *  - 본문 암호화: RFC 8291 (Message Encryption for Web Push) + RFC 8188 aes128gcm
 *  - 보낸 서버 증명: RFC 8292 VAPID (ES256 JWT)
 * 키는 모두 P-256 (secp256r1). 공개키는 65바이트 비압축 점(0x04 || X || Y), 개인키는 32바이트 스칼라를 base64url 로 주고받음
 */
public final class WebPushCrypto {

    private WebPushCrypto() {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final int RECORD_SIZE = 4096;
    private static final ECParameterSpec P256 = p256();

    // ---------- 키 ----------

    public static byte[] b64d(String s) {
        return B64D.decode(s.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    public static String b64(byte[] b) {
        return B64.encodeToString(b);
    }

    public static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 0x04) {
            throw new GeneralSecurityException("P-256 비압축 공개키(65바이트)가 아닙니다.");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
    }

    public static ECPrivateKey privateKey(byte[] scalar) throws GeneralSecurityException {
        if (scalar.length != 32) throw new GeneralSecurityException("P-256 개인키(32바이트)가 아닙니다.");
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), P256));
    }

    public static byte[] encode(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        copyFixed(key.getW().getAffineX(), out, 1);
        copyFixed(key.getW().getAffineY(), out, 33);
        return out;
    }

    public static KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        return g.generateKeyPair();
    }

    // ---------- 본문 암호화 (RFC 8291) ----------

    /**
     * @param uaPublic   구독의 p256dh (65바이트)
     * @param authSecret 구독의 auth (16바이트)
     * @return aes128gcm 본문 (헤더 + 암호문, 레코드 1개)
     */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
        return encrypt(plaintext, uaPublic, authSecret, newKeyPair(), randomBytes(16));
    }

    // 테스트에서 임시 키와 salt 를 고정하기 위한 버전
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair asKeys, byte[] salt)
            throws GeneralSecurityException {
        if (authSecret.length != 16) throw new GeneralSecurityException("auth 는 16바이트여야 합니다.");
        if (plaintext.length > RECORD_SIZE - 17 - 86) throw new GeneralSecurityException("본문이 너무 깁니다.");

        byte[] asPublic = encode((ECPublicKey) asKeys.getPublic());

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(asKeys.getPrivate());
        ka.doPhase(publicKey(uaPublic), true);
        byte[] ecdhSecret = ka.generateSecret();

        // IKM = HKDF(auth_secret, ecdh_secret, "WebPush: info" 0x00 ua_public as_public, 32)
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);

        byte[] prk = hmac(salt, ikm);
        byte[] cek = expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        // 마지막(유일한) 레코드: 본문 뒤에 구분 바이트 0x02
        byte[] padded = Arrays.copyOf(plaintext, plaintext.length + 1);
        padded[plaintext.length] = 0x02;

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] encrypted = c.doFinal(padded);

        // 헤더: salt(16) | rs(4) | idlen(1) | keyid(=as_public 65)
        ByteBuffer out = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length + encrypted.length);
        out.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic).put(encrypted);
        return out.array();
    }

    // ---------- VAPID (RFC 8292) ----------

    /**
     * Authorization 헤더 값: "vapid t=<JWT>, k=<서버 공개키>"
     * @param audience 푸시 서버 origin (예: https://fcm.googleapis.com)
     */
    public static String vapidAuthorization(String audience, String subject, long expiresEpochSec,
                                            ECPrivateKey vapidPrivate, byte[] vapidPublic) throws GeneralSecurityException {
        String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = b64(("{\"aud\":\"" + json(audience) + "\",\"exp\":" + expiresEpochSec
                + ",\"sub\":\"" + json(subject) + "\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;

        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(vapidPrivate);
        s.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        byte[] jose = derToJose(s.sign());

        return "vapid t=" + signingInput + "." + b64(jose) + ", k=" + b64(vapidPublic);
    }

    // JDK 서명은 DER(ASN.1) 형식 -> JWT 는 r || s 각 32바이트
    static byte[] derToJose(byte[] der) throws GeneralSecurityException {
        int offset = 2;
        if ((der[1] & 0x80) != 0) offset += der[1] & 0x7f; // 길이가 여러 바이트인 경우 (P-256 에선 거의 없음)
        if (der[offset] != 0x02) throw new GeneralSecurityException("잘못된 ECDSA 서명");
        int rLen = der[offset + 1];
        byte[] r = Arrays.copyOfRange(der, offset + 2, offset + 2 + rLen);
        int sOff = offset + 2 + rLen;
        if (der[sOff] != 0x02) throw new GeneralSecurityException("잘못된 ECDSA 서명");
        int sLen = der[sOff + 1];
        byte[] s = Arrays.copyOfRange(der, sOff + 2, sOff + 2 + sLen);
        byte[] out = new byte[64];
        copyFixed(new BigInteger(1, r), out, 0);
        copyFixed(new BigInteger(1, s), out, 32);
        return out;
    }

    // ---------- 내부 도구 ----------

    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int len) throws GeneralSecurityException {
        return expand(hmac(salt, ikm), info, len);
    }

    // HKDF-Expand 첫 블록만 (32바이트 이하만 필요)
    private static byte[] expand(byte[] prk, byte[] info, int len) throws GeneralSecurityException {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[]{0x01})), len);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.writeBytes(p);
        return o.toByteArray();
    }

    // 부호 바이트를 빼고 32바이트 자리에 오른쪽 정렬
    private static void copyFixed(BigInteger v, byte[] out, int off) {
        byte[] b = v.toByteArray();
        int start = b.length > 32 ? b.length - 32 : 0;
        int len = b.length - start;
        System.arraycopy(b, start, out, off + 32 - len, len);
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    private static String json(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
            p.init(new ECGenParameterSpec("secp256r1"));
            return p.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 을 지원하지 않는 JVM", e);
        }
    }
}
