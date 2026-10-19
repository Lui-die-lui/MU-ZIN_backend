package com.muzin.mu.zin.ai.provider;

import com.muzin.mu.zin.ai.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// OpenAI Responses API (POST {base-url}/responses) + function calling
// - 새 의존성 없이 Spring RestClient 로 호출. API 키는 서버 환경 변수(OPENAI_API)에서만 읽고 응답·로그에 남기지 않음
// - store=false: 대화를 OpenAI 에 저장하지 않으므로 이전 출력 항목(도구 호출·추론)을 다음 요청 input 에 직접 이어 붙임
@Slf4j
public class OpenAiResponsesProvider implements AiProvider {

    private final AiProperties props;
    private final ObjectMapper mapper;
    private final RestClient client;

    public OpenAiResponsesProvider(AiProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        AiProperties.OpenAi o = props.getOpenai();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(o.getConnectTimeoutSeconds())).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(o.getReadTimeoutSeconds()));
        this.client = RestClient.builder()
                .baseUrl(o.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + o.getApiKey())
                .build();
    }

    @Override
    public String name() {
        return AiProperties.PROVIDER_OPENAI;
    }

    @Override
    public Response respond(Request req) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", req.model());
        body.put("instructions", req.instructions());
        body.set("input", req.input());
        body.set("tools", req.tools());
        if (req.toolChoice() == null) {
            body.put("tool_choice", "auto");
        } else {
            ObjectNode choice = body.putObject("tool_choice");
            choice.put("type", "function");
            choice.put("name", req.toolChoice());
        }
        body.put("parallel_tool_calls", true);
        body.put("max_output_tokens", req.maxOutputTokens());
        body.put("store", props.getOpenai().isStore());
        if (props.getOpenai().isIncludeReasoningEncryptedContent()) {
            body.putArray("include").add("reasoning.encrypted_content");
        }

        String raw;
        try {
            raw = client.post().uri("/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException e) {
            // 4xx: 요청이 거절됨 (키·형식·한도). 408 만 처리 여부를 모름
            AiProviderException.Billing b = e.getStatusCode().value() == 408 ? AiProviderException.Billing.UNCERTAIN : AiProviderException.Billing.NOT_BILLED;
            log.warn("OpenAI 요청 거절: status={} body={}", e.getStatusCode().value(), abbreviate(e.getResponseBodyAsString()));
            throw new AiProviderException(b, "AI 제공자가 요청을 거절했습니다. (" + e.getStatusCode().value() + ")", null, e);
        } catch (HttpServerErrorException e) {
            log.warn("OpenAI 서버 오류: status={}", e.getStatusCode().value());
            throw new AiProviderException(AiProviderException.Billing.UNCERTAIN, "AI 제공자 서버 오류 (" + e.getStatusCode().value() + ")", null, e);
        } catch (ResourceAccessException e) {
            Throwable c = rootCause(e);
            boolean notSent = c instanceof ConnectException || c instanceof UnknownHostException || c instanceof HttpConnectTimeoutException;
            log.warn("OpenAI 연결 실패: {} ({})", c.getClass().getSimpleName(), notSent ? "보내지 못함" : "응답을 받지 못함");
            throw new AiProviderException(notSent ? AiProviderException.Billing.NOT_BILLED : AiProviderException.Billing.UNCERTAIN,
                    "AI 제공자에 연결하지 못했습니다.", null, e);
        } catch (RestClientException e) {
            throw new AiProviderException(AiProviderException.Billing.UNCERTAIN, "AI 제공자 응답을 처리하지 못했습니다.", null, e);
        }

        JsonNode root;
        try {
            root = mapper.readTree(raw);
        } catch (RuntimeException e) {
            throw new AiProviderException(AiProviderException.Billing.UNCERTAIN, "AI 응답 형식을 읽지 못했습니다.", null, e);
        }
        Usage usage = parseUsage(root.path("usage"));
        String status = root.path("status").asString("");
        if ("failed".equals(status) || "cancelled".equals(status)) {
            throw new AiProviderException(AiProviderException.Billing.KNOWN, "AI 응답이 실패 상태입니다: " + status, usage, null);
        }

        List<JsonNode> items = new ArrayList<>();
        List<FunctionCall> calls = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (JsonNode item : root.path("output")) {
            items.add(item);
            String type = item.path("type").asString("");
            if ("function_call".equals(type)) {
                calls.add(new FunctionCall(item.path("call_id").asString(""), item.path("name").asString(""), item.path("arguments").asString("{}")));
            } else if ("message".equals(type)) {
                for (JsonNode c : item.path("content")) {
                    if ("output_text".equals(c.path("type").asString(""))) text.append(c.path("text").asString(""));
                }
            }
        }
        return new Response(status, items, calls, text.toString(), usage);
    }

    private static Usage parseUsage(JsonNode u) {
        if (u == null || u.isMissingNode() || u.isNull()) return Usage.ZERO;
        return new Usage(u.path("input_tokens").asLong(0),
                u.path("input_tokens_details").path("cached_tokens").asLong(0),
                u.path("output_tokens").asLong(0));
    }

    private static Throwable rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c;
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
