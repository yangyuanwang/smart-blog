package com.blog.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * AI 摘要配置项（blog.ai.*）：OpenAI 兼容接口，未配置 api-key 时自动降级关闭。
 */
@Data
@Component
@ConfigurationProperties(prefix = "blog.ai")
public class AiProperties {

    /** 接口基础地址，如 https://open.bigmodel.cn/api/paas/v4 */
    private String baseUrl = "";

    /** API Key */
    private String apiKey = "";

    /** 模型名 */
    private String model = "glm-4-flash";

    /** 请求超时（秒） */
    private int timeoutSeconds = 10;

    /**
     * 是否可用：base-url 与 api-key 均已配置。
     *
     * @return 可用返回 true
     */
    public boolean enabled() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }
}
