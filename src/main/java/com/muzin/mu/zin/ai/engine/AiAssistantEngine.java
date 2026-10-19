package com.muzin.mu.zin.ai.engine;

import com.muzin.mu.zin.ai.config.AiProperties;
import com.muzin.mu.zin.ai.provider.AiProvider;
import com.muzin.mu.zin.ai.provider.AiProviderException;
import com.muzin.mu.zin.ai.tools.AiText;
import com.muzin.mu.zin.ai.tools.AiToolService;
import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.entity.TimePart;
import com.muzin.mu.zin.entity.lesson.LessonMode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

// 도구 호출형 AI 한 턴 실행 (DB 에서 근거를 조회 - 벡터 검색 없음)
// 사용자 질문 -> [모델이 검색 도구 호출 -> 서버가 DB 조회 결과를 돌려줌] x 최대 N 번 -> final_answer
// - 외부 호출 중 DB 트랜잭션을 열지 않음 (도구는 각자 짧은 읽기 전용 트랜잭션)
// - 매 호출의 입력 크기를 max-input-chars 안으로 묶음 -> 서비스가 호출 전에 "예상 최대 비용"을 계산할 수 있음
@Slf4j
@Component
public class AiAssistantEngine {

    public static final String FINAL_ANSWER = "final_answer";
    private static final int MAX_TOOL_OUTPUT_CHARS = 4000;
    private static final int MAX_CALLS_PER_ROUND = 3;
    private static final int MAX_MESSAGE_CHARS = 800;

    private final AiProperties props;
    private final ObjectMapper mapper;
    private final AiProvider provider;
    private final AiToolService tools;
    private final String instructions;
    private final ArrayNode toolDefs;

    public AiAssistantEngine(AiProperties props, ObjectMapper mapper, AiProvider provider, AiToolService tools) {
        this.props = props;
        this.mapper = mapper;
        this.provider = provider;
        this.tools = tools;
        this.instructions = load("ai/lesson-assistant-instructions.md");
        this.toolDefs = buildTools();
    }

    // ---------- 입력·결과 ----------

    public record HistoryItem(String role, String content) { }

    public record TurnRequest(String styleContextJson, List<HistoryItem> history, String userMessage) { }

    public record Answer(String message, List<AiToolService.RawRecommendation> recommendations, String followUpQuestion) { }

    public record TurnResult(Answer answer, AiProvider.Usage usage, int rounds) { }

    // 실패: usage = 응답을 받은 호출들의 정확한 사용량, lastCallBilling = 마지막 호출의 청구 여부
    @Getter
    public static class TurnFailure extends RuntimeException {
        private final AiProvider.Usage usage;
        private final AiProviderException.Billing lastCallBilling;

        public TurnFailure(String message, AiProvider.Usage usage, AiProviderException.Billing lastCallBilling, Throwable cause) {
            super(message, cause);
            this.usage = usage;
            this.lastCallBilling = lastCallBilling;
        }
    }

    // 한 번 호출의 최대 입력 글자 수 (지시문 + 도구 정의 + 대화 입력 상한) -> 비용 예약에 사용
    public long maxInputCharsPerCall() {
        return instructions.length() + toolDefs.toString().length() + props.getMaxInputChars();
    }

    // ---------- 실행 ----------

    public TurnResult run(TurnRequest req) {
        ArrayNode input = buildInput(req);
        AiProvider.Usage total = AiProvider.Usage.ZERO;
        int maxRounds = Math.max(1, props.getMaxToolRounds());

        for (int round = 1; round <= maxRounds; round++) {
            fitInput(input);
            boolean last = round == maxRounds;
            AiProvider.Response res;
            try {
                res = provider.respond(new AiProvider.Request(props.activeModel(), instructions, input.deepCopy(), toolDefs,
                        last ? FINAL_ANSWER : null, props.getMaxOutputTokens()));
            } catch (AiProviderException e) {
                AiProvider.Usage used = total.plus(e.getUsage());
                throw new TurnFailure(e.getMessage(), used, e.getBilling(), e);
            }
            total = total.plus(res.usage());

            AiProvider.FunctionCall finalCall = res.functionCalls().stream()
                    .filter(c -> FINAL_ANSWER.equals(c.name())).findFirst().orElse(null);
            if (finalCall != null) {
                return new TurnResult(parseAnswer(finalCall.arguments()), total, round);
            }
            if (res.functionCalls().isEmpty()) {
                if (res.text() != null && !res.text().isBlank()) {
                    // 함수 대신 글로만 답한 경우: 추천 없이 글만 (화면에 보일 글은 정리)
                    return new TurnResult(new Answer(AiText.sanitizeModelText(res.text(), MAX_MESSAGE_CHARS), List.of(), null), total, round);
                }
                throw new TurnFailure("AI 가 답변을 만들지 못했습니다. (status=" + res.status() + ")", total, AiProviderException.Billing.KNOWN, null);
            }

            // 도구 실행: 이번 응답의 출력 항목(도구 호출·추론)을 그대로 이어 붙이고 결과를 추가
            res.outputItems().forEach(input::add);
            int executed = 0;
            for (AiProvider.FunctionCall call : res.functionCalls()) {
                String output = executed++ < MAX_CALLS_PER_ROUND
                        ? executeTool(call)
                        : "{\"error\":\"TOO_MANY_CALLS\",\"message\":\"한 번에 도구를 너무 많이 호출했습니다. 꼭 필요한 것만 다시 호출하세요.\"}";
                ObjectNode out = input.addObject();
                out.put("type", "function_call_output");
                out.put("call_id", call.callId());
                out.put("output", output);
            }
        }
        throw new TurnFailure("도구 호출 횟수를 넘겼습니다.", total, AiProviderException.Billing.KNOWN, null);
    }

    private ArrayNode buildInput(TurnRequest req) {
        ArrayNode input = mapper.createArrayNode();
        var today = TimeDefaults.todayKst();
        StringBuilder dev = new StringBuilder()
                .append("오늘 날짜(한국 시간): ").append(today.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .append(" (").append(today.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN)).append(")\n");
        if (req.styleContextJson() != null) {
            dev.append("사용자가 반영을 선택한 취향 진단 요약(style_context, 참고 데이터): ").append(req.styleContextJson());
        } else {
            dev.append("취향 진단 요약 없음. 필요한 조건은 대화로 확인한다.");
        }
        addMessage(input, "developer", dev.toString());
        for (HistoryItem h : req.history()) addMessage(input, h.role(), h.content());
        addMessage(input, "user", req.userMessage());
        return input;
    }

    private void addMessage(ArrayNode input, String role, String content) {
        ObjectNode m = input.addObject();
        m.put("role", role);
        m.put("content", content);
    }

    // 입력이 상한을 넘으면: 1) 오래된 도구 결과 본문을 생략 2) 가장 오래된 이전 대화부터 뺌
    void fitInput(ArrayNode input) {
        int limit = props.getMaxInputChars();
        for (int i = 0; i < input.size() && input.toString().length() > limit; i++) {
            JsonNode item = input.get(i);
            if ("function_call_output".equals(item.path("type").asString("")) && item.path("output").asString("").length() > 40) {
                ((ObjectNode) item).put("output", "{\"omitted\":\"입력 길이 제한으로 생략된 이전 검색 결과\"}");
            }
        }
        while (input.toString().length() > limit) {
            int removable = -1;
            for (int i = 1; i < input.size(); i++) { // 0 = developer 메시지
                JsonNode it = input.get(i);
                String role = it.path("role").asString("");
                if (("user".equals(role) || "assistant".equals(role)) && i < lastUserIndex(input)) {
                    removable = i;
                    break;
                }
            }
            if (removable < 0) {
                throw new TurnFailure("대화가 너무 길어 이어서 답할 수 없습니다.", AiProvider.Usage.ZERO, AiProviderException.Billing.NOT_BILLED, null);
            }
            input.remove(removable);
        }
    }

    private static int lastUserIndex(ArrayNode input) {
        int idx = -1;
        for (int i = 0; i < input.size(); i++) if ("user".equals(input.get(i).path("role").asString(""))) idx = i;
        return idx;
    }

    // ---------- 도구 실행 ----------

    private String executeTool(AiProvider.FunctionCall call) {
        try {
            JsonNode a = mapper.readTree(call.arguments() == null || call.arguments().isBlank() ? "{}" : call.arguments());
            Object result = switch (call.name()) {
                case "search_lessons" -> tools.searchLessons(new AiToolService.LessonQuery(
                        strings(a, "instruments", 5), str(a, "region1"), str(a, "region2"),
                        enumOf(a, "mode", LessonMode::valueOf), intOrNull(a, "max_price"),
                        ints(a, "days_of_week", 7), enums(a, "time_parts", TimePart::valueOf),
                        str(a, "keyword"), strings(a, "style_tags", 5)));
                case "get_lesson" -> tools.getLesson(longOrZero(a, "lesson_id"));
                case "get_open_slots" -> tools.getOpenSlots(longOrZero(a, "lesson_id"), intOrNull(a, "days_ahead"), intOrNull(a, "limit"));
                case "search_artists" -> tools.searchArtists(new AiToolService.ArtistQuery(
                        strings(a, "instruments", 5), str(a, "region1"), str(a, "region2"), str(a, "keyword"), strings(a, "style_tags", 5)));
                case "get_artist" -> tools.getArtist(longOrZero(a, "artist_profile_id"));
                case "get_service_guide" -> tools.getServiceGuide(str(a, "topic"));
                default -> java.util.Map.of("error", "UNKNOWN_TOOL", "message", "없는 도구입니다.");
            };
            return capOutput(mapper.writeValueAsString(result));
        } catch (RuntimeException e) {
            log.warn("AI 도구 실행 실패: tool={} {}", call.name(), e.getClass().getSimpleName());
            return "{\"error\":\"TOOL_FAILED\",\"message\":\"도구를 실행하지 못했습니다. 인자를 확인하거나 다른 방법으로 찾아보세요.\"}";
        }
    }

    // 결과 JSON 이 크면 results 뒤쪽부터 줄임
    private String capOutput(String json) {
        if (json.length() <= MAX_TOOL_OUTPUT_CHARS) return json;
        try {
            JsonNode n = mapper.readTree(json);
            if (n.isObject() && n.path("results").isArray()) {
                ArrayNode results = (ArrayNode) n.path("results");
                while (results.size() > 1 && n.toString().length() > MAX_TOOL_OUTPUT_CHARS) results.remove(results.size() - 1);
                ((ObjectNode) n).put("truncated", true);
                if (n.toString().length() <= MAX_TOOL_OUTPUT_CHARS) return n.toString();
            }
        } catch (RuntimeException ignored) {
            // 아래에서 잘라서 반환
        }
        return "{\"truncated\":true,\"partial\":" + mapper.valueToTree(json.substring(0, MAX_TOOL_OUTPUT_CHARS - 100)) + "}";
    }

    private Answer parseAnswer(String arguments) {
        JsonNode a;
        try {
            a = mapper.readTree(arguments);
        } catch (RuntimeException e) {
            throw new TurnFailure("AI 답변 형식을 읽지 못했습니다.", AiProvider.Usage.ZERO, AiProviderException.Billing.KNOWN, e);
        }
        List<AiToolService.RawRecommendation> recs = new ArrayList<>();
        for (JsonNode r : a.path("recommendations")) {
            AiToolService.Kind kind = enumOf(r, "kind", AiToolService.Kind::valueOf);
            long id = r.path("id").asLong(0);
            if (kind != null && id > 0) recs.add(new AiToolService.RawRecommendation(kind, id, r.path("reason").asString("")));
        }
        String follow = str(a, "follow_up_question");
        return new Answer(AiText.sanitizeModelText(a.path("message").asString(""), MAX_MESSAGE_CHARS), recs,
                follow == null ? null : AiText.sanitizeModelText(follow, 200));
    }

    // ---------- 도구 정의 (strict: 모든 필드 required, 선택 값은 null 허용) ----------

    private ArrayNode buildTools() {
        ArrayNode t = mapper.createArrayNode();
        t.add(fn("search_lessons", "MU:ZIN 공개 레슨 검색. 조건에 맞는 레슨 최대 8개를 돌려준다. 가격은 원(KRW).",
                props(p -> {
                    p.set("instruments", nullableArray("string", "악기 이름 (예: 피아노, 통기타)"));
                    p.set("region1", nullable("string", "시·도 (예: 서울, 경기)"));
                    p.set("region2", nullable("string", "시·군·구 (예: 강남구). region1 이 있을 때만"));
                    p.set("mode", nullableEnum("수업 방식", "ONLINE", "OFFLINE", "VISIT", "REQUEST_ONLY"));
                    p.set("max_price", nullable("integer", "최대 가격(원)"));
                    p.set("days_of_week", nullableArray("integer", "예약 가능한 요일 1(월)~7(일)"));
                    p.set("time_parts", nullableEnumArray("예약 가능한 시간대", "DAWN", "MORNING", "AFTERNOON", "EVENING"));
                    p.set("keyword", nullable("string", "레슨 제목·소개에서 찾을 말"));
                    p.set("style_tags", nullableArray("string", "레슨 스타일 이름 (예: 기초부터 탄탄히)"));
                })));
        t.add(fn("get_lesson", "레슨 하나의 공개 상세 정보 (소개, 준비물, 스타일, 리뷰 요약).",
                props(p -> p.set("lesson_id", type("integer", "레슨 id")))));
        t.add(fn("get_open_slots", "레슨의 앞으로 예약 가능한(OPEN) 시간. 지난 시간은 나오지 않는다.",
                props(p -> {
                    p.set("lesson_id", type("integer", "레슨 id"));
                    p.set("days_ahead", nullable("integer", "오늘부터 며칠 안 (기본 28, 최대 56)"));
                    p.set("limit", nullable("integer", "최대 개수 (기본·최대 10)"));
                })));
        t.add(fn("search_artists", "MU:ZIN 승인 아티스트 검색. 최대 8명.",
                props(p -> {
                    p.set("instruments", nullableArray("string", "악기 이름"));
                    p.set("region1", nullable("string", "시·도"));
                    p.set("region2", nullable("string", "시·군·구"));
                    p.set("keyword", nullable("string", "이름·전공에서 찾을 말"));
                    p.set("style_tags", nullableArray("string", "레슨 스타일 이름"));
                })));
        t.add(fn("get_artist", "아티스트 공개 프로필 (전공, 악기, 활동 지역, 소개, 공개 레슨 목록, 리뷰 요약).",
                props(p -> p.set("artist_profile_id", type("integer", "아티스트 id")))));
        t.add(fn("get_service_guide", "MU:ZIN 이용 정책 안내 (예약·취소·문의·채팅·리뷰·결제·AI 이용). topic 을 null 로 두면 주제 목록.",
                props(p -> p.set("topic", nullableEnum("주제", tools.guideTopics().toArray(String[]::new))))));
        t.add(fn(FINAL_ANSWER, "사용자에게 보낼 최종 답변. 반드시 이 함수로 답한다.",
                props(p -> {
                    p.set("message", type("string", "한국어 2~5문장. 가격·예약 시간·URL 을 쓰지 않는다"));
                    ObjectNode items = mapper.createObjectNode();
                    items.put("type", "object");
                    items.put("additionalProperties", false);
                    ObjectNode ip = items.putObject("properties");
                    ObjectNode kind = ip.putObject("kind");
                    kind.put("type", "string");
                    kind.putArray("enum").add("LESSON").add("ARTIST");
                    ip.set("id", type("integer", "도구 결과에 있던 lesson_id 또는 artist_profile_id"));
                    ip.set("reason", type("string", "추천 이유 1~2문장 (도구 결과에 있는 사실만)"));
                    items.putArray("required").add("kind").add("id").add("reason");
                    ObjectNode recs = mapper.createObjectNode();
                    recs.put("type", "array");
                    recs.put("description", "추천 (최대 5개, 없으면 빈 배열)");
                    recs.set("items", items);
                    p.set("recommendations", recs);
                    p.set("follow_up_question", nullable("string", "조건을 더 물어볼 때 질문 하나"));
                })));
        return t;
    }

    private ObjectNode fn(String name, String description, ObjectNode properties) {
        ObjectNode f = mapper.createObjectNode();
        f.put("type", "function");
        f.put("name", name);
        f.put("description", description);
        ObjectNode params = f.putObject("parameters");
        params.put("type", "object");
        params.set("properties", properties);
        ArrayNode required = params.putArray("required");
        properties.propertyNames().forEach(required::add);
        params.put("additionalProperties", false);
        f.put("strict", true);
        return f;
    }

    private ObjectNode props(java.util.function.Consumer<ObjectNode> fill) {
        ObjectNode p = mapper.createObjectNode();
        fill.accept(p);
        return p;
    }

    private ObjectNode type(String type, String description) {
        ObjectNode n = mapper.createObjectNode();
        n.put("type", type);
        n.put("description", description);
        return n;
    }

    private ObjectNode nullable(String type, String description) {
        ObjectNode n = mapper.createObjectNode();
        n.putArray("type").add(type).add("null");
        n.put("description", description);
        return n;
    }

    private ObjectNode nullableEnum(String description, String... values) {
        ObjectNode n = mapper.createObjectNode();
        n.putArray("type").add("string").add("null");
        ArrayNode e = n.putArray("enum");
        for (String v : values) e.add(v);
        e.addNull();
        n.put("description", description);
        return n;
    }

    private ObjectNode nullableArray(String itemType, String description) {
        ObjectNode n = mapper.createObjectNode();
        n.putArray("type").add("array").add("null");
        n.putObject("items").put("type", itemType);
        n.put("description", description);
        return n;
    }

    private ObjectNode nullableEnumArray(String description, String... values) {
        ObjectNode n = nullableArray("string", description);
        ArrayNode e = ((ObjectNode) n.get("items")).putArray("enum");
        for (String v : values) e.add(v);
        return n;
    }

    // ---------- 인자 읽기 (strict 라도 방어적으로) ----------

    private static String str(JsonNode a, String f) {
        JsonNode v = a.path(f);
        if (!v.isString()) return null;
        String s = v.asString().trim();
        return s.isEmpty() ? null : (s.length() > 60 ? s.substring(0, 60) : s);
    }

    private static List<String> strings(JsonNode a, String f, int max) {
        List<String> out = new ArrayList<>();
        for (JsonNode v : a.path(f)) if (v.isString() && out.size() < max && !v.asString().isBlank()) out.add(v.asString().trim());
        return out.isEmpty() ? null : out;
    }

    private static List<Integer> ints(JsonNode a, String f, int max) {
        List<Integer> out = new ArrayList<>();
        for (JsonNode v : a.path(f)) if (v.canConvertToInt() && out.size() < max) out.add(v.asInt());
        return out.isEmpty() ? null : out;
    }

    private static Integer intOrNull(JsonNode a, String f) {
        JsonNode v = a.path(f);
        return v.isNumber() && v.canConvertToInt() ? v.asInt() : null;
    }

    private static long longOrZero(JsonNode a, String f) {
        JsonNode v = a.path(f);
        return v.isNumber() ? v.asLong() : 0L;
    }

    private static <E> E enumOf(JsonNode a, String f, Function<String, E> parse) {
        String s = str(a, f);
        if (s == null) return null;
        try {
            return parse.apply(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <E> List<E> enums(JsonNode a, String f, Function<String, E> parse) {
        List<E> out = new ArrayList<>();
        for (JsonNode v : a.path(f)) {
            if (!v.isString()) continue;
            try {
                out.add(parse.apply(v.asString().trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // 모르는 값은 무시
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(path + " 를 읽지 못했습니다.", e);
        }
    }
}
