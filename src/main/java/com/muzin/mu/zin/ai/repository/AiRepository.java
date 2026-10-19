package com.muzin.mu.zin.ai.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// AI 내비게이터 테이블 접근 (V10). 제한 검사는 모두 "조건부 UPDATE 한 번" -> 동시 요청에도 원자적
// 트랜잭션은 호출하는 쪽(AiAssistantService)이 짧게 잡음. 외부 AI 호출 중에는 트랜잭션을 열지 않음
@Repository
@RequiredArgsConstructor
public class AiRepository {

    private final NamedParameterJdbcTemplate jdbc;

    // ---------- 주간 사용량 ----------

    public int weeklyUsed(long userId, LocalDate weekStart) {
        List<Integer> r = jdbc.queryForList(
                "SELECT used FROM ai_weekly_usage WHERE user_id = :u AND week_start = :w",
                new MapSqlParameterSource("u", userId).addValue("w", weekStart), Integer.class);
        return r.isEmpty() ? 0 : r.get(0);
    }

    // 남은 횟수가 있으면 1 올리고 true. 같은 행을 잠그므로 동시 요청은 순서대로 판단됨
    public boolean tryReserveWeekly(long userId, LocalDate weekStart, int limit, LocalDateTime now) {
        MapSqlParameterSource p = new MapSqlParameterSource("u", userId).addValue("w", weekStart)
                .addValue("l", limit).addValue("now", now);
        jdbc.update("INSERT INTO ai_weekly_usage (user_id, week_start, used, update_dt) VALUES (:u, :w, 0, :now) " +
                "ON CONFLICT (user_id, week_start) DO NOTHING", p);
        return jdbc.update("UPDATE ai_weekly_usage SET used = used + 1, update_dt = :now " +
                "WHERE user_id = :u AND week_start = :w AND used < :l", p) == 1;
    }

    public void refundWeekly(long userId, LocalDate weekStart, LocalDateTime now) {
        jdbc.update("UPDATE ai_weekly_usage SET used = GREATEST(used - 1, 0), update_dt = :now " +
                        "WHERE user_id = :u AND week_start = :w",
                new MapSqlParameterSource("u", userId).addValue("w", weekStart).addValue("now", now));
    }

    // ---------- 월간 예산 ----------

    public record BudgetRow(String budgetKey, long capMicros, long reservedMicros, long spentMicros, long uncertainMicros) {
        public long remaining(long cap) {
            return Math.max(0, cap - spentMicros - reservedMicros);
        }
    }

    public Optional<BudgetRow> budget(String key) {
        return jdbc.query("SELECT * FROM ai_monthly_budget WHERE budget_key = :k", new MapSqlParameterSource("k", key),
                (rs, i) -> new BudgetRow(rs.getString("budget_key"), rs.getLong("cap_micros"), rs.getLong("reserved_micros"),
                        rs.getLong("spent_micros"), rs.getLong("uncertain_micros"))).stream().findFirst();
    }

    // 상한 안에서만 예약 (spent + reserved + amount <= cap). cap 은 현재 설정값으로 갱신
    public boolean tryReserveBudget(String key, long capMicros, long amount, LocalDateTime now) {
        MapSqlParameterSource p = new MapSqlParameterSource("k", key).addValue("cap", capMicros)
                .addValue("a", amount).addValue("now", now);
        jdbc.update("INSERT INTO ai_monthly_budget (budget_key, cap_micros, update_dt) VALUES (:k, :cap, :now) " +
                "ON CONFLICT (budget_key) DO NOTHING", p);
        return jdbc.update("UPDATE ai_monthly_budget SET reserved_micros = reserved_micros + :a, cap_micros = :cap, update_dt = :now " +
                "WHERE budget_key = :k AND spent_micros + reserved_micros + :a <= :cap", p) == 1;
    }

    // 예약 해제 + 실제 비용 반영. uncertain 은 spent 에 포함되는 "청구 여부를 모르는" 부분
    public void settleBudget(String key, long reserved, long spent, long uncertain, LocalDateTime now) {
        jdbc.update("UPDATE ai_monthly_budget SET reserved_micros = GREATEST(reserved_micros - :r, 0), " +
                        "spent_micros = spent_micros + :s, uncertain_micros = uncertain_micros + :u, update_dt = :now " +
                        "WHERE budget_key = :k",
                new MapSqlParameterSource("k", key).addValue("r", reserved).addValue("s", spent)
                        .addValue("u", uncertain).addValue("now", now));
    }

    // ---------- 대화 ----------

    public record ConversationRow(long conversationId, long userId, LocalDate weekStart, String status, String title,
                                  String styleContext, int userMessageCount, LocalDateTime inFlightUntil,
                                  String idempotencyKey, LocalDateTime createDt, LocalDateTime updateDt) {
        public boolean inFlight(LocalDateTime now) {
            return inFlightUntil != null && inFlightUntil.isAfter(now);
        }
    }

    private static final RowMapper<ConversationRow> CONV = (rs, i) -> new ConversationRow(
            rs.getLong("conversation_id"), rs.getLong("user_id"), rs.getObject("week_start", LocalDate.class),
            rs.getString("status"), rs.getString("title"), rs.getString("style_context"), rs.getInt("user_message_count"),
            toLdt(rs.getTimestamp("in_flight_until")), rs.getString("idempotency_key"),
            toLdt(rs.getTimestamp("create_dt")), toLdt(rs.getTimestamp("update_dt")));

    public Optional<ConversationRow> conversation(long conversationId, long userId) {
        return jdbc.query("SELECT * FROM ai_conversation WHERE conversation_id = :c AND user_id = :u",
                new MapSqlParameterSource("c", conversationId).addValue("u", userId), CONV).stream().findFirst();
    }

    public Optional<ConversationRow> conversationByIdem(long userId, String key) {
        return jdbc.query("SELECT * FROM ai_conversation WHERE user_id = :u AND idempotency_key = :k",
                new MapSqlParameterSource("u", userId).addValue("k", key), CONV).stream().findFirst();
    }

    public List<ConversationRow> conversations(long userId, int limit) {
        return jdbc.query("SELECT * FROM ai_conversation WHERE user_id = :u AND status <> 'PROCESSING' " +
                        "ORDER BY update_dt DESC, conversation_id DESC LIMIT :l",
                new MapSqlParameterSource("u", userId).addValue("l", limit), CONV);
    }

    // 첫 질문 처리 중(PROCESSING), 질문 1회, 처리 중 잠금. (user_id, idempotency_key) 중복이면 DuplicateKeyException
    public long insertConversation(long userId, LocalDate weekStart, String title, String styleContextJson,
                                   String idemKey, LocalDateTime now, LocalDateTime lockUntil) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO ai_conversation (user_id, week_start, status, title, style_context, user_message_count, " +
                        "in_flight_until, idempotency_key, create_dt, update_dt) " +
                        "VALUES (:u, :w, 'PROCESSING', :t, CAST(:sc AS jsonb), 1, :lock, :k, :now, :now)",
                new MapSqlParameterSource("u", userId).addValue("w", weekStart).addValue("t", title)
                        .addValue("sc", styleContextJson).addValue("lock", lockUntil).addValue("k", idemKey).addValue("now", now),
                kh, new String[]{"conversation_id"});
        return kh.getKey().longValue();
    }

    // 추가 질문: 열린 대화 + 질문 횟수 남음 + 처리 중 아님(또는 잠금 만료)일 때만 1 올리고 잠금
    public boolean tryLockForMessage(long conversationId, long userId, int maxUserMessages, LocalDateTime now, LocalDateTime lockUntil) {
        return jdbc.update("UPDATE ai_conversation SET user_message_count = user_message_count + 1, in_flight_until = :lock, update_dt = :now " +
                        "WHERE conversation_id = :c AND user_id = :u AND status = 'ACTIVE' AND user_message_count < :max " +
                        "AND (in_flight_until IS NULL OR in_flight_until < :now)",
                new MapSqlParameterSource("c", conversationId).addValue("u", userId).addValue("max", maxUserMessages)
                        .addValue("now", now).addValue("lock", lockUntil)) == 1;
    }

    // 답변 완료: 잠금 해제, 질문을 다 썼으면 CLOSED
    public void finishTurn(long conversationId, int maxUserMessages, LocalDateTime now) {
        jdbc.update("UPDATE ai_conversation SET in_flight_until = NULL, update_dt = :now, " +
                        "status = CASE WHEN user_message_count >= :max THEN 'CLOSED' ELSE 'ACTIVE' END " +
                        "WHERE conversation_id = :c",
                new MapSqlParameterSource("c", conversationId).addValue("max", maxUserMessages).addValue("now", now));
    }

    // 추가 질문 실패: 질문 횟수 돌려주고 잠금 해제
    public void rollbackTurn(long conversationId, LocalDateTime now) {
        jdbc.update("UPDATE ai_conversation SET user_message_count = GREATEST(user_message_count - 1, 0), " +
                        "in_flight_until = NULL, update_dt = :now WHERE conversation_id = :c",
                new MapSqlParameterSource("c", conversationId).addValue("now", now));
    }

    public int deleteConversation(long conversationId, long userId) {
        return jdbc.update("DELETE FROM ai_conversation WHERE conversation_id = :c AND user_id = :u AND " +
                        "(in_flight_until IS NULL OR in_flight_until < :now)",
                new MapSqlParameterSource("c", conversationId).addValue("u", userId)
                        .addValue("now", com.muzin.mu.zin.common.TimeDefaults.nowKst()));
    }

    // 실패한 첫 질문 정리 (내부용, 소유자 확인 없이 id 로)
    public void deleteConversationById(long conversationId) {
        jdbc.update("DELETE FROM ai_conversation WHERE conversation_id = :c", new MapSqlParameterSource("c", conversationId));
    }

    public int deleteAllConversations(long userId, LocalDateTime now) {
        return jdbc.update("DELETE FROM ai_conversation WHERE user_id = :u AND (in_flight_until IS NULL OR in_flight_until < :now)",
                new MapSqlParameterSource("u", userId).addValue("now", now));
    }

    // ---------- 메시지 ----------

    public record MessageRow(long messageId, long conversationId, String role, String content, String recommendations,
                             String status, String idempotencyKey, String model, Integer inputTokens,
                             Integer cachedInputTokens, Integer outputTokens, Long costMicros, String costStatus,
                             long reservedMicros, LocalDateTime createDt) { }

    private static final RowMapper<MessageRow> MSG = (rs, i) -> new MessageRow(
            rs.getLong("message_id"), rs.getLong("conversation_id"), rs.getString("role"), rs.getString("content"),
            rs.getString("recommendations"), rs.getString("status"), rs.getString("idempotency_key"), rs.getString("model"),
            (Integer) rs.getObject("input_tokens"), (Integer) rs.getObject("cached_input_tokens"),
            (Integer) rs.getObject("output_tokens"), (Long) rs.getObject("cost_micros"), rs.getString("cost_status"),
            rs.getLong("reserved_micros"), toLdt(rs.getTimestamp("create_dt")));

    public List<MessageRow> messages(long conversationId) {
        return jdbc.query("SELECT * FROM ai_message WHERE conversation_id = :c ORDER BY message_id",
                new MapSqlParameterSource("c", conversationId), MSG);
    }

    public Optional<MessageRow> messageByIdem(long conversationId, String key) {
        return jdbc.query("SELECT * FROM ai_message WHERE conversation_id = :c AND idempotency_key = :k",
                new MapSqlParameterSource("c", conversationId).addValue("k", key), MSG).stream().findFirst();
    }

    public long insertUserMessage(long conversationId, String content, String idemKey, long reservedMicros, String budgetKey,
                                  LocalDateTime now) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO ai_message (conversation_id, role, content, status, idempotency_key, reserved_micros, budget_key, create_dt) " +
                        "VALUES (:c, 'USER', :t, 'PROCESSING', :k, :r, :bk, :now)",
                new MapSqlParameterSource("c", conversationId).addValue("t", content).addValue("k", idemKey)
                        .addValue("r", reservedMicros).addValue("bk", budgetKey).addValue("now", now),
                kh, new String[]{"message_id"});
        return kh.getKey().longValue();
    }

    // 처리 중인 질문을 "완료"로 바꿈. 이미 다른 쪽(잠금 만료 복구)이 정리했으면 0 -> 정산하지 않음
    public boolean completeUserMessage(long messageId) {
        return jdbc.update("UPDATE ai_message SET status = 'OK', reserved_micros = 0 WHERE message_id = :m AND status = 'PROCESSING'",
                new MapSqlParameterSource("m", messageId)) == 1;
    }

    // 처리 중인 질문 행 삭제 (실패 정리). 이미 정리됐으면 0
    public boolean deleteProcessingUserMessage(long messageId) {
        return jdbc.update("DELETE FROM ai_message WHERE message_id = :m AND status = 'PROCESSING'",
                new MapSqlParameterSource("m", messageId)) == 1;
    }

    public void insertAssistantMessage(long conversationId, String content, String recommendationsJson, String model,
                                       long inputTokens, long cachedInputTokens, long outputTokens, long costMicros,
                                       String costStatus, LocalDateTime now) {
        jdbc.update("INSERT INTO ai_message (conversation_id, role, content, recommendations, status, model, input_tokens, " +
                        "cached_input_tokens, output_tokens, cost_micros, cost_status, create_dt) " +
                        "VALUES (:c, 'ASSISTANT', :t, CAST(:rec AS jsonb), 'OK', :model, :in, :cin, :out, :cost, :cs, :now)",
                new MapSqlParameterSource("c", conversationId).addValue("t", content).addValue("rec", recommendationsJson)
                        .addValue("model", model).addValue("in", inputTokens).addValue("cin", cachedInputTokens)
                        .addValue("out", outputTokens).addValue("cost", costMicros).addValue("cs", costStatus).addValue("now", now));
    }

    // ---------- 복구·정리 ----------

    // 잠금이 만료됐는데 아직 처리 중인 질문 (서버 중단 등으로 정산되지 않은 것)
    public record StaleRow(long messageId, long conversationId, long userId, LocalDate weekStart, String convStatus, long reservedMicros,
                           String budgetKey) { }

    public List<StaleRow> staleProcessing(LocalDateTime lockExpiredBefore) {
        return jdbc.query("SELECT m.message_id, m.conversation_id, c.user_id, c.week_start, c.status, m.reserved_micros, m.budget_key " +
                        "FROM ai_message m JOIN ai_conversation c ON c.conversation_id = m.conversation_id " +
                        "WHERE m.status = 'PROCESSING' AND (c.in_flight_until IS NULL OR c.in_flight_until < :t)",
                new MapSqlParameterSource("t", lockExpiredBefore),
                (rs, i) -> new StaleRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getObject(4, LocalDate.class),
                        rs.getString(5), rs.getLong(6), rs.getString(7)));
    }

    public int deleteConversationsUpdatedBefore(LocalDateTime cutoff) {
        return jdbc.update("DELETE FROM ai_conversation WHERE update_dt < :t AND (in_flight_until IS NULL OR in_flight_until < :t)",
                new MapSqlParameterSource("t", cutoff));
    }

    public int deleteWeeklyUsageBefore(LocalDate weekStart) {
        return jdbc.update("DELETE FROM ai_weekly_usage WHERE week_start < :w", new MapSqlParameterSource("w", weekStart));
    }

    private static LocalDateTime toLdt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
