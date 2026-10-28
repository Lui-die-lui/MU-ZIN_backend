package com.muzin.mu.zin.service;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.auth.WithdrawRequest;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.OAuth2UserRepository;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.security.model.PrincipalUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class WithdrawalService {

    private static final String CONFIRMATION = "탈퇴합니다";

    private final UserRepository users;
    private final OAuth2UserRepository oauth2Users;
    private final PasswordEncoder passwordEncoder;
    private final NamedParameterJdbcTemplate jdbc;
    private final byte[] hmacSecret;
    private final int rejoinBlockMonths;

    public WithdrawalService(UserRepository users,
                             OAuth2UserRepository oauth2Users,
                             PasswordEncoder passwordEncoder,
                             NamedParameterJdbcTemplate jdbc,
                             @Value("${app.withdraw.email-hmac-secret}") String hmacSecret,
                             @Value("${app.withdraw.rejoin-block-months}") int rejoinBlockMonths) {
        if (hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalStateException("WITHDRAW_EMAIL_HMAC_SECRET 설정이 필요합니다.");
        }
        if (rejoinBlockMonths < 1) {
            throw new IllegalStateException("REJOIN_BLOCK_MONTHS는 1 이상이어야 합니다.");
        }
        this.users = users;
        this.oauth2Users = oauth2Users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.hmacSecret = hmacSecret.getBytes(StandardCharsets.UTF_8);
        this.rejoinBlockMonths = rejoinBlockMonths;
    }

    @Transactional(readOnly = true)
    public void checkRejoinAllowed(String email) {
        String hash = emailHmac(email);
        Integer blocked = jdbc.queryForObject("""
                select count(*) from rejoin_block
                where email_hmac = :hash and blocked_until > :now
                """, new MapSqlParameterSource("hash", hash).addValue("now", TimeDefaults.nowKst()), Integer.class);
        if (blocked != null && blocked > 0) {
            throw ApiException.conflict("REJOIN_BLOCKED", "이용 제한 이력이 있는 탈퇴 계정은 정해진 기간 동안 다시 가입할 수 없습니다.");
        }
    }

    @Transactional
    public void withdraw(PrincipalUser principal, WithdrawRequest request) {
        if (request == null || !CONFIRMATION.equals(request.confirmation())) {
            throw ApiException.badRequest("확인 문구로 ‘탈퇴합니다’를 입력해 주세요.");
        }

        User user = users.findById(principal.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "해당 유저가 존재하지 않습니다."));
        if (user.isWithdrawn()) {
            throw ApiException.unauthorized("ACCOUNT_WITHDRAWN", "이미 탈퇴한 계정입니다.");
        }

        boolean social = oauth2Users.existsByUser_UserId(user.getUserId());
        if (!social && (request.password() == null || !passwordEncoder.matches(request.password(), user.getPassword()))) {
            throw ApiException.unauthorized("INVALID_PASSWORD", "비밀번호가 올바르지 않습니다.");
        }

        MapSqlParameterSource id = new MapSqlParameterSource("userId", user.getUserId());
        Integer active = jdbc.queryForObject("""
                select count(*)
                from lesson_reservation r
                join lessons l on l.lesson_id = r.lesson_id
                join artist_profile ap on ap.artist_profile_id = l.artist_profile_id
                where (r.user_id = :userId or ap.user_id = :userId)
                  and r.status in ('REQUESTED', 'CONFIRMED', 'COMPLETION_PENDING')
                """, id, Integer.class);
        if (active != null && active > 0) {
            throw ApiException.conflict("ACTIVE_RESERVATION", "진행 중인 예약이 있어 탈퇴할 수 없습니다. 예약을 취소하거나 마무리해 주세요.");
        }

        LocalDateTime now = TimeDefaults.nowKst();
        if (user.isEverSuspended()) {
            jdbc.update("""
                    insert into rejoin_block (email_hmac, blocked_until, create_dt)
                    values (:hash, :until, :now)
                    on conflict (email_hmac) do update
                    set blocked_until = greatest(rejoin_block.blocked_until, excluded.blocked_until)
                    """, new MapSqlParameterSource("hash", emailHmac(user.getEmail()))
                    .addValue("until", now.plusMonths(rejoinBlockMonths)).addValue("now", now));
        }

        // 개인 계정에만 속한 데이터는 삭제한다. 채팅·리뷰·예약·문의·동의·신고는 공유/운영 기록이라 FK와 함께 남긴다.
        jdbc.update("delete from push_subscription where user_id = :userId", id);
        jdbc.update("delete from notification where user_id = :userId", id);
        jdbc.update("delete from artist_favorite where user_id = :userId or artist_profile_id in (select artist_profile_id from artist_profile where user_id = :userId)", id);
        jdbc.update("delete from ai_conversation where user_id = :userId", id);
        jdbc.update("delete from ai_weekly_usage where user_id = :userId", id);
        jdbc.update("delete from login_failure_log where user_id = :userId", id);
        jdbc.update("delete from oauth2_user where user_id = :userId", id);
        jdbc.update("delete from user_role where user_id = :userId", id);
        jdbc.update("delete from artist_instruments where artist_profile_id in (select artist_profile_id from artist_profile where user_id = :userId)", id);
        jdbc.update("delete from artist_service_region where artist_profile_id in (select artist_profile_id from artist_profile where user_id = :userId)", id);
        jdbc.update("delete from artist_style_map where artist_profile_id in (select artist_profile_id from artist_profile where user_id = :userId)", id);
        jdbc.update("update lessons set status = 'INACTIVE' where artist_profile_id in (select artist_profile_id from artist_profile where user_id = :userId)", id);
        jdbc.update("""
                update artist_profile set bio = '', career = '', major_name = '',
                    rejected_reason_title = null, rejected_reason = null,
                    region1_depth_name = null, region2_depth_name = null, region3_depth_name = null,
                    address_label = null, road_address = null, jibun_address = null, detail_address = null,
                    latitude = null, longitude = null
                where user_id = :userId
                """, id);

        String tombstone = UUID.randomUUID().toString();
        jdbc.update("""
                update users set email = :email, username = :username, password = :password,
                    profile_img_url = null, email_verified = false, artist_status = 'NONE',
                    suspended_dt = null, suspended_reason = null, withdrawn_dt = :now
                where user_id = :userId
                """, id.addValue("email", "withdrawn-" + tombstone + "@invalid.local")
                .addValue("username", "withdrawn-" + tombstone)
                .addValue("password", passwordEncoder.encode(UUID.randomUUID().toString()))
                .addValue("now", now));
    }

    @Scheduled(cron = "0 10 4 * * *", zone = TimeDefaults.DEFAULT_TZ)
    @Transactional
    public void deleteExpiredRejoinBlocks() {
        jdbc.update("delete from rejoin_block where blocked_until <= :now",
                new MapSqlParameterSource("now", TimeDefaults.nowKst()));
    }

    private String emailHmac(String email) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("이메일 HMAC 계산에 실패했습니다.", e);
        }
    }
}
