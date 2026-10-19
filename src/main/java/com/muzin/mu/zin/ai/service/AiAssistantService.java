package com.muzin.mu.zin.ai.service;

import com.muzin.mu.zin.ai.config.AiProperties;
import com.muzin.mu.zin.ai.context.StyleContextValidator;
import com.muzin.mu.zin.ai.dto.AiAssistantDtos.*;
import com.muzin.mu.zin.ai.engine.AiAssistantEngine;
import com.muzin.mu.zin.ai.provider.AiProvider;
import com.muzin.mu.zin.ai.provider.AiProviderException;
import com.muzin.mu.zin.ai.repository.AiRepository;
import com.muzin.mu.zin.ai.repository.AiRepository.ConversationRow;
import com.muzin.mu.zin.ai.repository.AiRepository.MessageRow;
import com.muzin.mu.zin.ai.support.AiCost;
import com.muzin.mu.zin.ai.support.AiWeek;
import com.muzin.mu.zin.ai.tools.AiToolService;
import com.muzin.mu.zin.common.TimeDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// AI 레슨 내비게이터 흐름
// 대화 시작 / 질문 추가 모두 같은 3단계 - 외부 AI 호출 중에는 DB 트랜잭션을 열지 않음
//  1) 예약 (짧은 트랜잭션): 주간 횟수(시작만) 또는 대화 질문 횟수·처리 중 잠금 + 월간 예산 예상 최대액 + 질문 저장
//  2) AI 호출 (트랜잭션 없음): 도구 호출형 엔진
//  3) 정산 (짧은 트랜잭션): 성공 -> 답변 저장·실제 비용 정산 / 실패 -> 사용자 횟수 돌려줌 + 비용은 아는 만큼 + 모르는 호출은 보수 정산
// 정산은 "처리 중인 질문 행"을 조건부로 바꾼 쪽만 진행 -> 잠금 만료 복구와 겹쳐도 두 번 정산되지 않음
@Slf4j
@Service
public class AiAssistantService {

    private static final Pattern IDEM = Pattern.compile("^[A-Za-z0-9_.:-]{8,64}$");
    private static final int LIST_LIMIT = 30;

    private final AiProperties props;
    private final AiRepository repo;
    private final AiAssistantEngine engine;
    private final AiToolService tools;
    private final StyleContextValidator contextValidator;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public AiAssistantService(AiProperties props, AiRepository repo, AiAssistantEngine engine, AiToolService tools,
                              StyleContextValidator contextValidator, ObjectMapper mapper, PlatformTransactionManager txManager) {
        this.props = props;
        this.repo = repo;
        this.engine = engine;
        this.tools = tools;
        this.contextValidator = contextValidator;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---------- 이용량 ----------

    public QuotaResponse quota(long userId) {
        LocalDateTime now = TimeDefaults.nowKst();
        var week = AiWeek.weekStart(now);
        int used = repo.weeklyUsed(userId, week);
        int limit = props.getWeeklyConversationLimit();
        String code = null, msg = null;
        if (unavailableReason() != null) {
            code = AiErrors.UNAVAILABLE;
            msg = AiErrors.unavailable().getMessage();
        } else if (!budgetHasRoom(now)) {
            code = AiErrors.BUDGET_EXHAUSTED;
            msg = AiErrors.budgetExhausted().getMessage();
        }
        return new QuotaResponse(limit, Math.min(used, limit), Math.max(0, limit - used), week, AiWeek.nextReset(now),
                props.getMaxUserMessages(), code == null, code, msg);
    }

    // ---------- 대화 시작 (주간 1회 차감) ----------

    public ConversationDetail start(long userId, String idemKey, StartRequest req) {
        String key = checkKey(idemKey);
        String message = req.message().trim();
        String context = contextValidator.validate(req.styleContext());

        var existing = repo.conversationByIdem(userId, key);
        if (existing.isPresent()) return replayStart(userId, existing.get(), message);
        requireAvailable();

        LocalDateTime now = TimeDefaults.nowKst();
        var week = AiWeek.weekStart(now);
        String budgetKey = budgetKey(now);
        long reserve = turnReserveMicros();
        long[] ids;
        try {
            ids = tx.execute(s -> {
                if (!repo.tryReserveWeekly(userId, week, props.getWeeklyConversationLimit(), now)) {
                    throw AiErrors.weeklyLimit(Duration.between(now, AiWeek.nextReset(now)).toSeconds());
                }
                // 예산이 없으면 예외 -> 롤백되어 주간 횟수도 차감되지 않음
                if (!repo.tryReserveBudget(budgetKey, capMicros(), reserve, now)) throw AiErrors.budgetExhausted();
                long c = repo.insertConversation(userId, week, title(message), context, key, now, now.plusSeconds(props.inFlightLockSeconds()));
                long m = repo.insertUserMessage(c, message, key, reserve, budgetKey, now);
                return new long[]{c, m};
            });
        } catch (DuplicateKeyException e) {
            // 같은 Idempotency-Key 로 동시에 들어온 요청: 먼저 저장된 쪽만 진행 (이쪽 차감은 롤백됨)
            return replayStart(userId, repo.conversationByIdem(userId, key).orElseThrow(AiErrors::inProgress), message);
        }

        runAndSettle(userId, ids[0], ids[1], week, budgetKey, reserve, new AiAssistantEngine.TurnRequest(context, List.of(), message), true);
        return detail(userId, ids[0]);
    }

    // ---------- 질문 추가 (주간 횟수 차감 없음, 대화당 최대 6번) ----------

    public ConversationDetail send(long userId, long conversationId, String idemKey, MessageRequest req) {
        String key = checkKey(idemKey);
        String message = req.message().trim();
        ConversationRow c = repo.conversation(conversationId, userId).orElseThrow(AiErrors::notFound);

        var replay = repo.messageByIdem(conversationId, key);
        if (replay.isPresent()) return replayMessage(userId, conversationId, replay.get(), message);

        LocalDateTime now = TimeDefaults.nowKst();
        if ("CLOSED".equals(c.status()) || c.userMessageCount() >= props.getMaxUserMessages()) throw AiErrors.messageLimit();
        if ("PROCESSING".equals(c.status()) || c.inFlight(now)) throw AiErrors.inProgress();
        requireAvailable();

        List<AiAssistantEngine.HistoryItem> history = history(repo.messages(conversationId));
        String budgetKey = budgetKey(now);
        long reserve = turnReserveMicros();
        long msgId;
        try {
            msgId = tx.execute(s -> {
                if (!repo.tryLockForMessage(conversationId, userId, props.getMaxUserMessages(), now, now.plusSeconds(props.inFlightLockSeconds()))) {
                    ConversationRow fresh = repo.conversation(conversationId, userId).orElseThrow(AiErrors::notFound);
                    if (fresh.userMessageCount() >= props.getMaxUserMessages() || "CLOSED".equals(fresh.status())) throw AiErrors.messageLimit();
                    throw AiErrors.inProgress();
                }
                if (!repo.tryReserveBudget(budgetKey, capMicros(), reserve, now)) throw AiErrors.budgetExhausted();
                return repo.insertUserMessage(conversationId, message, key, reserve, budgetKey, now);
            });
        } catch (DuplicateKeyException e) {
            MessageRow m = repo.messageByIdem(conversationId, key).orElseThrow(AiErrors::inProgress);
            return replayMessage(userId, conversationId, m, message);
        }

        runAndSettle(userId, conversationId, msgId, c.weekStart(), budgetKey, reserve,
                new AiAssistantEngine.TurnRequest(c.styleContext(), history, message), false);
        return detail(userId, conversationId);
    }

    // ---------- 2) 호출 + 3) 정산 ----------

    private void runAndSettle(long userId, long conversationId, long userMessageId, java.time.LocalDate week, String budgetKey,
                              long reserve, AiAssistantEngine.TurnRequest turn, boolean first) {
        AiProperties.ModelPrice price = price();
        AiAssistantEngine.TurnResult result;
        List<AiToolService.ValidRecommendation> valid;
        try {
            result = engine.run(turn);
            valid = tools.validate(result.answer().recommendations()); // 모델이 고른 id 를 공개 조건으로 다시 확인
        } catch (AiAssistantEngine.TurnFailure f) {
            long known = cost(price, f.getUsage());
            long uncertain = f.getLastCallBilling() == AiProviderException.Billing.UNCERTAIN ? perCallMaxMicros(price) : 0;
            settleFailure(userId, conversationId, userMessageId, week, budgetKey, reserve, known + uncertain, uncertain, first);
            log.warn("AI 답변 실패: conversation={} billing={} known={}µ$ uncertain={}µ$ reason={}",
                    conversationId, f.getLastCallBilling(), known, uncertain, f.getMessage());
            throw AiErrors.aiError(first);
        } catch (RuntimeException e) {
            // 예상하지 못한 오류: 몇 번 호출됐는지 모르므로 예약액 전부를 "청구 불명확"으로 정산
            settleFailure(userId, conversationId, userMessageId, week, budgetKey, reserve, reserve, reserve, first);
            log.error("AI 처리 중 예상하지 못한 오류: conversation={}", conversationId, e);
            throw AiErrors.aiError(first);
        }

        AiProvider.Usage u = result.usage();
        long actual = cost(price, u);
        if (actual > reserve) log.warn("AI 실제 비용이 예약액보다 큼: actual={} reserve={}", actual, reserve);
        String recJson = recommendationsJson(valid, result.answer().followUpQuestion());
        LocalDateTime done = TimeDefaults.nowKst();
        Boolean ok = tx.execute(s -> {
            if (!repo.completeUserMessage(userMessageId)) return false; // 이미 잠금 만료 복구가 정리함
            repo.insertAssistantMessage(conversationId, result.answer().message(), recJson, props.activeModel(),
                    u.inputTokens(), u.cachedInputTokens(), u.outputTokens(), actual, "EXACT", done);
            repo.settleBudget(budgetKey, reserve, actual, 0, done);
            repo.finishTurn(conversationId, props.getMaxUserMessages(), done);
            return true;
        });
        if (!Boolean.TRUE.equals(ok)) throw AiErrors.aiError(first);
    }

    private void settleFailure(long userId, long conversationId, long userMessageId, java.time.LocalDate week, String budgetKey,
                               long reserve, long spent, long uncertain, boolean first) {
        LocalDateTime now = TimeDefaults.nowKst();
        tx.executeWithoutResult(s -> {
            if (!repo.deleteProcessingUserMessage(userMessageId)) return; // 이미 정리됨
            repo.settleBudget(budgetKey, reserve, spent, uncertain, now);
            if (first) {
                repo.refundWeekly(userId, week, now); // 답변을 못 받았으니 주간 횟수 돌려줌
                repo.deleteConversationById(conversationId);
            } else {
                repo.rollbackTurn(conversationId, now); // 질문 횟수 돌려줌 + 잠금 해제
            }
        });
    }

    // ---------- 조회·삭제 ----------

    public List<ConversationSummary> list(long userId) {
        return repo.conversations(userId, LIST_LIMIT).stream()
                .map(c -> new ConversationSummary(c.conversationId(), c.title(), c.status(), c.userMessageCount(),
                        props.getMaxUserMessages(), c.createDt(), c.updateDt()))
                .toList();
    }

    public ConversationDetail detail(long userId, long conversationId) {
        ConversationRow c = repo.conversation(conversationId, userId).orElseThrow(AiErrors::notFound);
        LocalDateTime now = TimeDefaults.nowKst();
        List<MessageView> views = new ArrayList<>();
        for (MessageRow m : repo.messages(conversationId)) {
            if ("USER".equals(m.role())) {
                views.add(new MessageView(m.messageId(), "USER", m.content(), null, List.of(), "PROCESSING".equals(m.status()), m.createDt()));
            } else {
                JsonNode rec = readJson(m.recommendations());
                List<AiToolService.ValidRecommendation> items = new ArrayList<>();
                for (JsonNode r : rec.path("items")) {
                    items.add(new AiToolService.ValidRecommendation(AiToolService.Kind.valueOf(r.path("kind").asString()),
                            r.path("id").asLong(), r.path("reason").asString(null)));
                }
                String follow = rec.path("followUpQuestion").isString() ? rec.path("followUpQuestion").asString() : null;
                // 카드 값(가격·지역·예약 가능 시간·링크)은 지금 DB 에서 다시 채움
                views.add(new MessageView(m.messageId(), "ASSISTANT", m.content(), follow, tools.cards(items), false, m.createDt()));
            }
        }
        int max = props.getMaxUserMessages();
        return new ConversationDetail(c.conversationId(), c.title(), c.status(), contextSummary(c.styleContext()),
                c.userMessageCount(), max, Math.max(0, max - c.userMessageCount()), c.inFlight(now),
                c.createDt(), c.updateDt(), views);
    }

    public void delete(long userId, long conversationId) {
        ConversationRow c = repo.conversation(conversationId, userId).orElseThrow(AiErrors::notFound);
        if (c.inFlight(TimeDefaults.nowKst())) throw AiErrors.inProgress();
        if (repo.deleteConversation(conversationId, userId) == 0) throw AiErrors.inProgress();
    }

    public int deleteAll(long userId) {
        return repo.deleteAllConversations(userId, TimeDefaults.nowKst());
    }

    // ---------- 복구·정리 (AiMaintenanceJob) ----------

    // 처리 중 잠금이 만료됐는데 정산되지 않은 질문 (서버 중단 등): 결과를 모르므로 예약액을 "청구 불명확"으로 정산하고 횟수는 돌려줌
    public int recoverStale() {
        LocalDateTime now = TimeDefaults.nowKst();
        int n = 0;
        for (AiRepository.StaleRow r : repo.staleProcessing(now)) {
            Boolean done = tx.execute(s -> {
                if (!repo.deleteProcessingUserMessage(r.messageId())) return false;
                if (r.budgetKey() != null) repo.settleBudget(r.budgetKey(), r.reservedMicros(), r.reservedMicros(), r.reservedMicros(), now);
                if ("PROCESSING".equals(r.convStatus())) {
                    repo.refundWeekly(r.userId(), r.weekStart(), now);
                    repo.deleteConversationById(r.conversationId());
                } else {
                    repo.rollbackTurn(r.conversationId(), now);
                }
                return true;
            });
            if (Boolean.TRUE.equals(done)) n++;
        }
        if (n > 0) log.warn("AI 처리 중 잠금 만료 복구: {}건 (예약액을 청구 불명확으로 정산)", n);
        return n;
    }

    // 보관 기간(기본 90일)이 지난 대화·메시지와 주간 사용량 삭제. 월간 비용 합계(개인정보 없음)는 남김
    public int cleanupRetention() {
        LocalDateTime now = TimeDefaults.nowKst();
        int conv = repo.deleteConversationsUpdatedBefore(now.minusDays(props.getRetentionDays()));
        repo.deleteWeeklyUsageBefore(AiWeek.weekStart(now).minusDays(props.getRetentionDays()));
        return conv;
    }

    // ---------- 비용 ----------

    // 호출 1번의 예상 최대 비용 (입력 상한 글자 수를 토큰 상한으로 + 최대 출력)
    long perCallMaxMicros(AiProperties.ModelPrice price) {
        return AiCost.maxMicros(price, AiCost.estimateTokensUpperBound(engine.maxInputCharsPerCall()), props.getMaxOutputTokens());
    }

    // 한 턴(최대 도구 호출 횟수만큼)의 예상 최대 비용 -> 호출 전에 예약
    long turnReserveMicros() {
        return perCallMaxMicros(price()) * Math.max(1, props.getMaxToolRounds());
    }

    long capMicros() {
        return AiCost.usdToMicros(props.getMonthlyBudgetUsd());
    }

    String budgetKey(LocalDateTime now) {
        return props.getBudgetKeyPrefix() + (props.isStub() ? "stub" : "openai") + ":" + AiWeek.monthKey(now);
    }

    private boolean budgetHasRoom(LocalDateTime now) {
        long cap = capMicros();
        long need = turnReserveMicros();
        return repo.budget(budgetKey(now)).map(b -> cap - b.spentMicros() - b.reservedMicros() >= need).orElse(cap >= need);
    }

    private AiProperties.ModelPrice price() {
        AiProperties.ModelPrice p = props.getPricing().getModels().get(props.activeModel());
        if (p != null && p.isComplete()) return p;
        if (props.isStub()) { // stub 단가가 없으면 0원으로 (실제 과금 없음)
            AiProperties.ModelPrice zero = new AiProperties.ModelPrice();
            zero.setInputUsdPerMtok(BigDecimal.ZERO);
            zero.setCachedInputUsdPerMtok(BigDecimal.ZERO);
            zero.setOutputUsdPerMtok(BigDecimal.ZERO);
            return zero;
        }
        throw AiErrors.unavailable();
    }

    private static long cost(AiProperties.ModelPrice price, AiProvider.Usage u) {
        return AiCost.actualMicros(price, u.inputTokens(), u.cachedInputTokens(), u.outputTokens());
    }

    // openai 인데 키·단가가 없으면 호출하지 않음 (stub 으로 몰래 바꾸지 않음)
    String unavailableReason() {
        if (props.isStub()) return null;
        if (props.getOpenai().getApiKey() == null || props.getOpenai().getApiKey().isBlank()) return "OPENAI_API 가 설정되지 않음";
        AiProperties.ModelPrice p = props.getPricing().getModels().get(props.getModel());
        if (p == null || !p.isComplete()) return "모델 단가가 설정되지 않음: " + props.getModel();
        return null;
    }

    private void requireAvailable() {
        String reason = unavailableReason();
        if (reason != null) {
            log.warn("AI 레슨 내비게이터 사용 불가: {}", reason);
            throw AiErrors.unavailable();
        }
    }

    // ---------- 도움 ----------

    private ConversationDetail replayStart(long userId, ConversationRow c, String message) {
        if ("PROCESSING".equals(c.status())) throw AiErrors.inProgress();
        String firstQuestion = repo.messages(c.conversationId()).stream().filter(m -> "USER".equals(m.role()))
                .map(MessageRow::content).findFirst().orElse("");
        if (!firstQuestion.equals(message)) throw AiErrors.idempotencyKeyReused();
        return detail(userId, c.conversationId());
    }

    private ConversationDetail replayMessage(long userId, long conversationId, MessageRow m, String message) {
        if ("PROCESSING".equals(m.status())) throw AiErrors.inProgress();
        if (!m.content().equals(message)) throw AiErrors.idempotencyKeyReused();
        return detail(userId, conversationId);
    }

    private static String checkKey(String key) {
        if (key == null || key.isBlank()) throw AiErrors.idempotencyKeyRequired();
        String k = key.trim();
        if (!IDEM.matcher(k).matches()) throw AiErrors.idempotencyKeyRequired();
        return k;
    }

    private static String title(String message) {
        String t = message.replaceAll("\\s+", " ").trim();
        return t.length() > 40 ? t.substring(0, 40) + "…" : t;
    }

    // 이전 대화 (완료된 것만, 최근 N개). 답변에는 추천했던 id 를 함께 적어 이어지는 질문에서 참고하게 함
    private List<AiAssistantEngine.HistoryItem> history(List<MessageRow> messages) {
        List<AiAssistantEngine.HistoryItem> out = new ArrayList<>();
        for (MessageRow m : messages) {
            if (!"OK".equals(m.status())) continue;
            if ("USER".equals(m.role())) {
                out.add(new AiAssistantEngine.HistoryItem("user", m.content()));
            } else {
                StringBuilder sb = new StringBuilder(m.content());
                JsonNode rec = readJson(m.recommendations());
                List<String> ids = new ArrayList<>();
                for (JsonNode r : rec.path("items")) ids.add(r.path("kind").asString() + ":" + r.path("id").asLong());
                if (!ids.isEmpty()) sb.append("\n[이전 답변에서 추천한 id] ").append(String.join(", ", ids));
                out.add(new AiAssistantEngine.HistoryItem("assistant", sb.toString()));
            }
        }
        int max = Math.max(0, props.getMaxHistoryMessages());
        return out.size() > max ? out.subList(out.size() - max, out.size()) : out;
    }

    private String recommendationsJson(List<AiToolService.ValidRecommendation> valid, String followUp) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode items = root.putArray("items");
        for (AiToolService.ValidRecommendation r : valid) {
            ObjectNode o = items.addObject();
            o.put("kind", r.kind().name());
            o.put("id", r.id());
            if (r.reason() == null) o.putNull("reason"); else o.put("reason", r.reason());
        }
        if (followUp == null) root.putNull("followUpQuestion"); else root.put("followUpQuestion", followUp);
        return root.toString();
    }

    private StyleContextSummary contextSummary(String json) {
        if (json == null) return null;
        JsonNode c = readJson(json);
        List<String> recs = new ArrayList<>();
        for (JsonNode r : c.path("recommended")) recs.add(r.path("name").asString(""));
        List<String> keywords = new ArrayList<>();
        for (JsonNode k : c.path("styleKeywords")) keywords.add(k.asString(""));
        JsonNode rg = c.path("region");
        String region = rg.isObject()
                ? String.join(" ", java.util.stream.Stream.of(rg.path("region1DepthName"), rg.path("region2DepthName"))
                .filter(JsonNode::isString).map(JsonNode::asString).toList())
                : null;
        return new StyleContextSummary(recs, region == null || region.isBlank() ? null : region, keywords);
    }

    private JsonNode readJson(String json) {
        if (json == null) return mapper.createObjectNode();
        try {
            return mapper.readTree(json);
        } catch (RuntimeException e) {
            return mapper.createObjectNode();
        }
    }
}
