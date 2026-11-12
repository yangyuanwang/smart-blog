package com.blog.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI 兼容大模型客户端：POST {base-url}/chat/completions，取 choices[0].message.content。
 * 失败/超时统一抛出调用异常，由上层决定降级行为。
 */
@Slf4j
@Component
public class LlmClient {

    /**
     * 单次补全结果。
     *
     * @param content 模型输出文本
     */
    public record Completion(String content) {
    }

    private final AiProperties properties;
    private final RestClient restClient;

    /**
     * 构造客户端（读超时按配置生成）。
     *
     * @param properties AI 配置
     */
    public LlmClient(AiProperties properties) {
        this.properties = properties;
        var requestFactory = new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(3, properties.getTimeoutSeconds())));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 请求一次对话补全。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 模型输出
     * @throws IllegalStateException 接口未配置、调用失败或超时
     */
    public Completion complete(String systemPrompt, String userPrompt) {
        if (!properties.enabled()) {
            throw new IllegalStateException("AI 接口未配置（blog.ai.base-url / api-key）");
        }
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.5);
        try {
            var response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class)
                    .getBody();
            return new Completion(extractContent(response));
        } catch (Exception ex) {
            log.warn("LLM 调用失败：{}", ex.getMessage());
            throw new IllegalStateException("LLM 调用失败", ex);
        }
    }

    /**
     * 从 OpenAI 兼容响应中提取文本（choices[0].message.content）。
     *
     * @param response 响应 Map
     * @return 输出文本；结构异常时返回空串
     */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object choices = response.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> first) {
            Object message = first.get("message");
            if (message instanceof Map<?, ?> msg) {
                Object content = msg.get("content");
                return content == null ? "" : String.valueOf(content);
            }
        }
        return "";
    }
}
