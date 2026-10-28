package com.muzin.mu.zin.service.push;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.push.PushSubscriptionRepository;
import com.muzin.mu.zin.repository.push.PushSubscriptionRepository.Subscription;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/*
 * 웹 푸시 (표준 Web Push + VAPID)
 *  - 구독 등록·해제: 로그인한 유저(서버 인증 정보) 기준. 클라이언트가 보낸 userId 는 받지 않음
 *  - 발송: 별도 작업 스레드에서 (호출한 요청·트랜잭션은 기다리지 않음, 실패해도 영향 없음)
 *  - 푸시 서버가 404/410 이면 구독 삭제, 일시 오류가 계속되면(10번) 삭제
 *  - 키(WEBPUSH_PUBLIC_KEY / WEBPUSH_PRIVATE_KEY)가 없으면 푸시 기능 꺼짐 (나머지 기능은 그대로)
 *  - 전달·표시는 브라우저·OS 가 결정하므로 항상 도착한다고 보장할 수 없음
 */
@Slf4j
@Service
public class PushService {

    static final int MAX_DEVICES_PER_USER = 10;
    static final int DELETE_AFTER_FAILURES = 10;

    // 서버가 요청을 보낼 수 있는 푸시 서버만 허용 (임의 주소로 요청을 보내게 만드는 공격 방지)
    private static final List<String> PUSH_HOSTS = List.of(
            "fcm.googleapis.com", "android.googleapis.com",   // Chrome · Edge(안드로이드) · 삼성 인터넷 등
            "push.services.mozilla.com",                       // Firefox
            "push.apple.com",                                  // Safari (iOS 16.4+ 홈 화면 웹앱, macOS)
            "notify.windows.com"                               // Edge (Windows)
    );
    private static final Pattern TOPIC = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    private final PushSubscriptionRepository repo;
    private final String subject;
    private final byte[] publicKey;
    private final ECPrivateKey privateKey;
    private final HttpClient http;
    private final ThreadPoolExecutor executor;

    public PushService(
            PushSubscriptionRepository repo,
            @Value("${app.webpush.public-key:}") String publicKeyB64,
            @Value("${app.webpush.private-key:}") String privateKeyB64,
            @Value("${app.webpush.subject:}") String subject,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.repo = repo;
        this.subject = subject == null || subject.isBlank() ? frontendUrl : subject.trim();
        byte[] pub = null;
        ECPrivateKey priv = null;
        if (!publicKeyB64.isBlank() && !privateKeyB64.isBlank()) {
            try {
                pub = WebPushCrypto.b64d(publicKeyB64);
                priv = WebPushCrypto.privateKey(WebPushCrypto.b64d(privateKeyB64));
                WebPushCrypto.publicKey(pub); // 형식 확인
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                log.error("웹 푸시 키 형식이 잘못되어 푸시를 끕니다: {}", e.getMessage());
                pub = null;
                priv = null;
            }
        }
        this.publicKey = pub;
        this.privateKey = priv;
        if (!enabled()) log.info("웹 푸시 꺼짐 (WEBPUSH_PUBLIC_KEY / WEBPUSH_PRIVATE_KEY 없음)");

        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        // 작업 스레드 2개, 대기 500건. 넘치면 버림 (푸시 때문에 서버가 밀리지 않게)
        this.executor = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500), r -> {
            Thread t = new Thread(r, "web-push");
            t.setDaemon(true);
            return t;
        });
    }

    public boolean enabled() {
        return publicKey != null && privateKey != null;
    }

    public String publicKey() {
        return enabled() ? WebPushCrypto.b64(publicKey) : null;
    }

    // ---------- 구독 ----------

    public void subscribe(long userId, String endpoint, String p256dh, String auth, String userAgent) {
        if (!enabled()) throw ApiException.conflict("PUSH_DISABLED", "지금은 알림 기능을 사용할 수 없어요.");
        String ep = validEndpoint(endpoint);
        try {
            byte[] key = WebPushCrypto.b64d(p256dh);
            WebPushCrypto.publicKey(key);
            if (WebPushCrypto.b64d(auth).length != 16) throw new GeneralSecurityException("auth");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw ApiException.badRequest("알림 구독 정보가 올바르지 않습니다.");
        }
        String ua = userAgent == null ? null : userAgent.substring(0, Math.min(200, userAgent.length()));
        repo.upsert(userId, ep, p256dh.trim(), auth.trim(), ua, TimeDefaults.nowKst());
        repo.keepLatest(userId, MAX_DEVICES_PER_USER);
    }

    public void unsubscribe(long userId, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return;
        repo.deleteOwn(userId, endpoint.trim());
    }

    public boolean isSubscribed(long userId, String endpoint) {
        return endpoint != null && repo.exists(userId, endpoint.trim());
    }

    static String validEndpoint(String endpoint) {
        if (endpoint == null || endpoint.length() > 1024) throw ApiException.badRequest("알림 구독 주소가 올바르지 않습니다.");
        try {
            URI u = URI.create(endpoint.trim());
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            boolean allowed = "https".equals(u.getScheme()) && u.getPort() == -1 && u.getUserInfo() == null
                    && PUSH_HOSTS.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h));
            if (!allowed) throw ApiException.badRequest("지원하지 않는 알림 서비스입니다.");
            return u.toString();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("알림 구독 주소가 올바르지 않습니다.");
        }
    }

    // ---------- 발송 ----------

    /**
     * 유저의 모든 기기로 보냄 (비동기). 호출한 쪽은 결과를 기다리지 않음
     * @param topic 같은 topic 의 아직 안 보낸 알림은 푸시 서버가 마지막 것 하나로 합침 (예: 같은 대화방)
     * @param ttlSeconds 기기가 꺼져 있을 때 푸시 서버가 보관하는 시간
     */
    public void sendToUser(long userId, PushMessage message, String topic, int ttlSeconds) {
        if (!enabled()) return;
        try {
            executor.execute(() -> deliverAll(userId, message, topic, ttlSeconds));
        } catch (RejectedExecutionException e) {
            log.warn("푸시 대기열이 가득 차서 버림: user={}", userId);
        }
    }

    private void deliverAll(long userId, PushMessage message, String topic, int ttlSeconds) {
        try {
            byte[] payload = message.toJson().getBytes(StandardCharsets.UTF_8);
            for (Subscription s : repo.findByUser(userId)) {
                deliver(s, payload, topic, ttlSeconds);
            }
        } catch (RuntimeException e) {
            log.warn("푸시 발송 중 오류: user={} {}", userId, e.toString());
        }
    }

    private void deliver(Subscription s, byte[] payload, String topic, int ttlSeconds) {
        try {
            URI endpoint = URI.create(s.endpoint());
            String audience = endpoint.getScheme() + "://" + endpoint.getHost();
            long exp = Instant.now().plus(Duration.ofHours(12)).getEpochSecond();
            byte[] body = WebPushCrypto.encrypt(payload, WebPushCrypto.b64d(s.p256dh()), WebPushCrypto.b64d(s.auth()));

            HttpRequest.Builder req = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", WebPushCrypto.vapidAuthorization(audience, subject, exp, privateKey, publicKey))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", String.valueOf(ttlSeconds))
                    .header("Urgency", "high")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            if (topic != null && TOPIC.matcher(topic).matches()) req.header("Topic", topic);

            int status = http.send(req.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
            applyResult(s.id(), status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            repo.markFailure(s.id(), DELETE_AFTER_FAILURES);
            log.info("푸시 전송 실패: id={} {}", s.id(), e.toString());
        }
    }

    // 푸시 서버 응답에 따라 구독 정리
    void applyResult(long subscriptionId, int status) {
        if (status >= 200 && status < 300) {
            repo.markSuccess(subscriptionId, TimeDefaults.nowKst());
        } else if (status == 404 || status == 410) {
            repo.deleteById(subscriptionId); // 만료·해제된 구독
            log.info("만료된 푸시 구독 삭제: id={} status={}", subscriptionId, status);
        } else if (status == 400 || status == 403 || status == 413) {
            // 요청 자체 문제(키 불일치 등) - 다시 보내도 같으므로 실패로 누적
            repo.markFailure(subscriptionId, DELETE_AFTER_FAILURES);
            log.warn("푸시 거절: id={} status={}", subscriptionId, status);
        } else {
            repo.markFailure(subscriptionId, DELETE_AFTER_FAILURES); // 429, 5xx 등 일시 오류
            log.info("푸시 일시 실패: id={} status={}", subscriptionId, status);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
