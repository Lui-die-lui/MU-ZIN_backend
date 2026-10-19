package com.muzin.mu.zin.ai.context;

import com.muzin.mu.zin.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// 취향 진단 요약(프론트 engine.ts 의 toAssistantContext, 스키마 muzin.style-finder.context/v2) 검증
// 프론트가 보낸 값이므로 허용 필드·형식·길이·전체 크기를 확인하고, 통과한 값만 새로 만들어 돌려줌 (모르는 필드는 거부)
@Component
public class StyleContextValidator {

    public static final String SCHEMA = "muzin.style-finder.context/v2";
    public static final int MAX_JSON_CHARS = 4000;

    private static final Set<String> TOP = Set.of("schema", "quizVersion", "styleType", "styleKeywords", "recommended", "preferences", "region");
    private static final Set<String> PREFS = Set.of("roles", "social", "pace", "timbre", "music", "voice");
    private static final Set<String> REC = Set.of("id", "name", "instrumentNames");
    private static final Set<String> REGION = Set.of("region1DepthName", "region2DepthName", "region3DepthName");
    private static final Set<String> ROLES = Set.of("melody", "rhythm", "voice", "harmony");
    private static final Pattern TOKEN = Pattern.compile("^[a-z0-9_|:.-]{1,40}$");

    private final ObjectMapper mapper;

    public StyleContextValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // null 이면 취향 반영 안 함. 통과하면 정리된 JSON 문자열
    public String validate(JsonNode raw) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) return null;
        if (!raw.isObject()) throw invalid("취향 정보 형식이 올바르지 않아요.");
        if (raw.toString().length() > MAX_JSON_CHARS) throw invalid("취향 정보가 너무 커요.");
        onlyFields(raw, TOP);
        if (!SCHEMA.equals(text(raw, "schema", 60, true))) throw invalid("지원하지 않는 취향 정보 형식이에요.");

        ObjectNode out = mapper.createObjectNode();
        out.put("schema", SCHEMA);
        out.put("quizVersion", text(raw, "quizVersion", 40, true));
        out.put("styleType", token(raw, "styleType", true));

        ArrayNode keywords = out.putArray("styleKeywords");
        for (JsonNode k : array(raw, "styleKeywords", 6)) keywords.add(str(k, 30));

        ArrayNode recs = out.putArray("recommended");
        for (JsonNode r : array(raw, "recommended", 6)) {
            if (!r.isObject()) throw invalid("추천 후보 형식이 올바르지 않아요.");
            onlyFields(r, REC);
            ObjectNode o = recs.addObject();
            o.put("id", token(r, "id", true));
            o.put("name", text(r, "name", 30, true));
            ArrayNode names = o.putArray("instrumentNames");
            for (JsonNode n : array(r, "instrumentNames", 5)) names.add(str(n, 30));
        }

        JsonNode prefs = raw.get("preferences");
        if (prefs != null && !prefs.isNull()) {
            if (!prefs.isObject()) throw invalid("취향 정보 형식이 올바르지 않아요.");
            onlyFields(prefs, PREFS);
            ObjectNode p = out.putObject("preferences");
            ArrayNode roles = p.putArray("roles");
            for (JsonNode role : array(prefs, "roles", 4)) {
                String v = str(role, 10);
                if (!ROLES.contains(v)) throw invalid("취향 정보 형식이 올바르지 않아요.");
                roles.add(v);
            }
            for (String f : new String[]{"social", "pace", "timbre", "music", "voice"}) {
                String v = token(prefs, f, false);
                if (v == null) p.putNull(f); else p.put(f, v);
            }
        }

        JsonNode region = raw.get("region");
        if (region != null && !region.isNull()) {
            if (!region.isObject()) throw invalid("지역 형식이 올바르지 않아요.");
            onlyFields(region, REGION);
            ObjectNode rg = out.putObject("region");
            rg.put("region1DepthName", text(region, "region1DepthName", 30, true));
            String r2 = text(region, "region2DepthName", 30, false);
            String r3 = text(region, "region3DepthName", 30, false);
            if (r2 == null) rg.putNull("region2DepthName"); else rg.put("region2DepthName", r2);
            if (r3 == null) rg.putNull("region3DepthName"); else rg.put("region3DepthName", r3);
        } else {
            out.putNull("region");
        }
        return out.toString();
    }

    private static void onlyFields(JsonNode node, Set<String> allowed) {
        Iterator<Map.Entry<String, JsonNode>> it = node.properties().iterator();
        while (it.hasNext()) {
            String name = it.next().getKey();
            if (!allowed.contains(name)) throw invalid("취향 정보에 알 수 없는 항목이 있어요.");
        }
    }

    private static Iterable<JsonNode> array(JsonNode parent, String field, int max) {
        JsonNode a = parent.get(field);
        if (a == null || a.isNull()) return java.util.List.of();
        if (!a.isArray() || a.size() > max) throw invalid("취향 정보 형식이 올바르지 않아요.");
        return a;
    }

    private static String text(JsonNode parent, String field, int max, boolean required) {
        JsonNode v = parent.get(field);
        if (v == null || v.isNull()) {
            if (required) throw invalid("취향 정보에 빠진 항목이 있어요.");
            return null;
        }
        return str(v, max);
    }

    private static String str(JsonNode v, int max) {
        if (!v.isString()) throw invalid("취향 정보 형식이 올바르지 않아요.");
        String s = v.asString().trim();
        if (s.isEmpty() || s.length() > max) throw invalid("취향 정보 형식이 올바르지 않아요.");
        // 제어 문자 제거 (프롬프트에 그대로 들어가므로)
        return s.replaceAll("\\p{Cntrl}", " ");
    }

    private static String token(JsonNode parent, String field, boolean required) {
        String v = text(parent, field, 40, required);
        if (v != null && !TOKEN.matcher(v).matches()) throw invalid("취향 정보 형식이 올바르지 않아요.");
        return v;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "AI_CONTEXT_INVALID", message);
    }
}
