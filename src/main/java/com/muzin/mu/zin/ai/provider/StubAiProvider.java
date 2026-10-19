package com.muzin.mu.zin.ai.provider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 가짜 AI (기본값, 네트워크 호출·과금 없음). 개발·테스트에서 전체 흐름(도구 호출 -> 최종 답변 -> 검증·정산)을 확인하는 용도
// 1번째 호출: 질문에서 악기·지역 단어를 찾아 search_lessons (또는 search_artists / get_service_guide) 호출
// 2번째 호출: 도구 결과의 앞쪽 id 로 final_answer
// 테스트용 표시 (질문에 넣으면 동작이 바뀜):
//   [stub:fail-before]  호출 전에 실패 (청구 없음)       [stub:fail-after] 호출 뒤 결과 모름 (청구 불명확)
//   [stub:slow:1500]    1.5초 기다린 뒤 진행            [stub:invalid]    없는 레슨 id 를 추천에 섞음
//   [stub:price-text]   답변 글에 금액·URL 을 넣음        [stub:artist]     아티스트 검색
//   [stub:guide:<topic>] 서비스 안내 조회                [stub:fail-second] 2번째 호출(결과 정리)에서 결과 모름
//   [stub:kw:<말>]       검색 keyword 로 넣음
public class StubAiProvider implements AiProvider {

    private static final String[] INSTRUMENTS = {"피아노", "통기타", "일렉기타", "베이스기타", "기타", "드럼", "바이올린", "첼로",
            "플루트", "색소폰", "트럼펫", "보컬", "가야금", "콘트라베이스", "클라리넷", "우쿨렐레"};
    private static final String[] REGIONS = {"서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원",
            "충북", "충남", "전북", "전남", "경북", "경남", "제주"};
    private static final Pattern SLOW = Pattern.compile("\\[stub:slow:(\\d{1,5})]");
    private static final Pattern GUIDE = Pattern.compile("\\[stub:guide:([a-z_]+)]");
    private static final Pattern KEYWORD = Pattern.compile("\\[stub:kw:([^\\]]{1,40})]");

    private final ObjectMapper mapper;

    public StubAiProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public Response respond(Request req) {
        String question = lastUserText(req.input());
        Matcher slow = SLOW.matcher(question);
        if (slow.find()) sleep(Long.parseLong(slow.group(1)));
        if (question.contains("[stub:fail-before]")) {
            throw new AiProviderException(AiProviderException.Billing.NOT_BILLED, "stub: 호출 전 실패");
        }
        boolean hasToolOutput = lastToolOutput(req.input()) != null;
        if (question.contains("[stub:fail-after]") || (hasToolOutput && question.contains("[stub:fail-second]"))) {
            throw new AiProviderException(AiProviderException.Billing.UNCERTAIN, "stub: 호출 뒤 결과 모름");
        }

        Usage usage = new Usage(Math.min(req.input().toString().length() + req.instructions().length(), 50_000), 0, 120);
        if (!hasToolOutput && !"final_answer".equals(req.toolChoice())) {
            ObjectNode args = mapper.createObjectNode();
            String tool;
            Matcher guide = GUIDE.matcher(question);
            if (guide.find()) {
                tool = "get_service_guide";
                args.put("topic", guide.group(1));
            } else {
                tool = question.contains("[stub:artist]") ? "search_artists" : "search_lessons";
                ArrayNode insts = args.putArray("instruments");
                for (String i : INSTRUMENTS) if (question.contains(i)) { insts.add(i); break; }
                String region = null;
                for (String r : REGIONS) if (question.contains(r)) { region = r; break; }
                if (region == null) args.putNull("region1"); else args.put("region1", region);
                args.putNull("region2");
                Matcher kw = KEYWORD.matcher(question);
                if (kw.find()) args.put("keyword", kw.group(1)); else args.putNull("keyword");
                args.putNull("style_tags");
                if (tool.equals("search_lessons")) {
                    args.putNull("mode");
                    args.putNull("max_price");
                    args.putNull("days_of_week");
                    args.putNull("time_parts");
                }
            }
            return call(tool, args, usage);
        }

        // 결과 정리: 도구 결과 앞쪽에서 최대 3개 추천
        JsonNode out = lastToolOutput(req.input());
        ObjectNode answer = mapper.createObjectNode();
        ArrayNode recs = answer.putArray("recommendations");
        if (out != null) {
            for (JsonNode r : out.path("results")) {
                if (recs.size() >= 3) break;
                ObjectNode rec = recs.addObject();
                if (r.has("lesson_id")) {
                    rec.put("kind", "LESSON");
                    rec.put("id", r.path("lesson_id").asLong());
                } else {
                    rec.put("kind", "ARTIST");
                    rec.put("id", r.path("artist_profile_id").asLong());
                }
                rec.put("reason", "요청하신 조건과 맞아서 골랐어요. (stub)");
            }
        }
        if (question.contains("[stub:invalid]")) {
            ObjectNode bad = recs.addObject();
            bad.put("kind", "LESSON");
            bad.put("id", 987_654_321L);
            bad.put("reason", "존재하지 않는 레슨 (stub)");
        }
        String message;
        if (out != null && out.has("guide")) {
            message = out.path("title").asString("") + " 안내예요. " + out.path("guide").asString("").lines().findFirst().orElse("");
            answer.putNull("follow_up_question");
        } else if (recs.isEmpty()) {
            message = "조건에 맞는 레슨을 찾지 못했어요. (stub)";
            answer.put("follow_up_question", "다른 지역이나 악기로 찾아볼까요?");
        } else {
            message = "조건에 맞는 선택지를 찾았어요. (stub)";
            answer.putNull("follow_up_question");
        }
        if (question.contains("[stub:price-text]")) {
            message += " 이 레슨은 30,000원이고 https://example.com/lesson 에서 볼 수 있어요.";
        }
        answer.put("message", message);
        return call("final_answer", answer, usage);
    }

    private Response call(String name, ObjectNode args, Usage usage) {
        String callId = "call_" + UUID.randomUUID().toString().substring(0, 8);
        ObjectNode item = mapper.createObjectNode();
        item.put("type", "function_call");
        item.put("id", "fc_" + callId);
        item.put("call_id", callId);
        item.put("name", name);
        item.put("arguments", args.toString());
        List<JsonNode> items = new ArrayList<>();
        items.add(item);
        return new Response("completed", items, List.of(new FunctionCall(callId, name, args.toString())), "", usage);
    }

    private static String lastUserText(ArrayNode input) {
        String text = "";
        for (JsonNode i : input) {
            if ("user".equals(i.path("role").asString(""))) text = i.path("content").asString("");
        }
        return text;
    }

    // 마지막 사용자 질문 뒤에 붙은 도구 결과 (없으면 null)
    private JsonNode lastToolOutput(ArrayNode input) {
        JsonNode last = null;
        for (JsonNode i : input) {
            if ("user".equals(i.path("role").asString(""))) last = null;
            if ("function_call_output".equals(i.path("type").asString(""))) {
                try {
                    last = mapper.readTree(i.path("output").asString("{}"));
                } catch (RuntimeException e) {
                    last = mapper.createObjectNode();
                }
            }
        }
        return last;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(Math.min(ms, 20_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
