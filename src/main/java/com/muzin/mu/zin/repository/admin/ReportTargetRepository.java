package com.muzin.mu.zin.repository.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

// 신고 대상 찾기 - 대상이 누구인지(target 유저), 신고하는 사람이 볼 수 있는 대상인지, 신고 당시 내용
@Repository
@RequiredArgsConstructor
public class ReportTargetRepository {

    private static final DateTimeFormatter MSG_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final NamedParameterJdbcTemplate jdbc;

    // 찾은 대상: 신고받는 유저 + 관리자에게 보여줄 내용
    public record Target(long userId, String snapshot) { }

    // 아티스트 프로필 (공개 페이지라 누구나 신고 가능)
    public Optional<Target> artist(long artistProfileId) {
        return jdbc.query("""
                SELECT ap.user_id, u.username, ap.bio
                FROM artist_profile ap JOIN users u ON u.user_id = ap.user_id
                WHERE ap.artist_profile_id = :id
                """, new MapSqlParameterSource("id", artistProfileId), (rs, i) -> new Target(
                rs.getLong("user_id"),
                "아티스트: " + rs.getString("username") + nullToEmpty("\n소개: ", rs.getString("bio"))
        )).stream().findFirst();
    }

    // 대화방: 신고자가 참여자여야 하고, 대상은 상대방. 상대가 보낸 최근 메시지 20개를 남김
    public Optional<Target> chatRoomCounterpart(long chatRoomId, long reporterId) {
        Optional<Long> counterpart = jdbc.query("""
                SELECT CASE WHEN r.user_id = :me THEN ap.user_id ELSE r.user_id END AS other
                FROM chat_room r JOIN artist_profile ap ON ap.artist_profile_id = r.artist_profile_id
                WHERE r.chat_room_id = :id AND (r.user_id = :me OR ap.user_id = :me)
                """, new MapSqlParameterSource("id", chatRoomId).addValue("me", reporterId),
                (rs, i) -> rs.getLong("other")).stream().findFirst();
        if (counterpart.isEmpty()) return Optional.empty();

        List<String> lines = jdbc.query("""
                SELECT content, create_dt FROM chat_message
                WHERE chat_room_id = :id AND sender_user_id = :other AND deleted_dt IS NULL
                ORDER BY chat_message_id DESC LIMIT 20
                """, new MapSqlParameterSource("id", chatRoomId).addValue("other", counterpart.get()),
                (rs, i) -> "[" + toTime(rs.getTimestamp("create_dt")) + "] " + rs.getString("content"));
        List<String> oldestFirst = new ArrayList<>(lines);
        Collections.reverse(oldestFirst);
        String snapshot = lines.isEmpty() ? "(상대가 보낸 메시지 없음)" : String.join("\n", oldestFirst);
        return Optional.of(new Target(counterpart.get(), snapshot));
    }

    // 리뷰 (공개, 삭제 안 된 것만). 대상은 작성자
    public Optional<Target> review(long reviewId) {
        return jdbc.query("""
                SELECT review_user_id, rating, content FROM review
                WHERE review_id = :id AND delete_dt IS NULL
                """, new MapSqlParameterSource("id", reviewId), (rs, i) -> new Target(
                rs.getLong("review_user_id"),
                "별점 " + rs.getInt("rating") + "점" + nullToEmpty("\n", rs.getString("content"))
        )).stream().findFirst();
    }

    // 받은 문의: 문의받은 아티스트만 신고 가능. 대상은 문의한 사람
    public Optional<Target> inquiry(long inquiryId, long reporterId) {
        return jdbc.query("""
                SELECT i.user_id, i.content
                FROM lesson_inquiry i JOIN artist_profile ap ON ap.artist_profile_id = i.artist_profile_id
                WHERE i.inquiry_id = :id AND ap.user_id = :me
                """, new MapSqlParameterSource("id", inquiryId).addValue("me", reporterId), (rs, i) -> new Target(
                rs.getLong("user_id"), rs.getString("content")
        )).stream().findFirst();
    }

    private static String nullToEmpty(String prefix, String s) {
        return s == null || s.isBlank() ? "" : prefix + s;
    }

    private static String toTime(Timestamp t) {
        return t == null ? "" : t.toLocalDateTime().format(MSG_TIME);
    }
}
