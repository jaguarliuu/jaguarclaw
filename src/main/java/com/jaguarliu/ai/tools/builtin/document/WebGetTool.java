package com.jaguarliu.ai.tools.builtin.document;

import com.jaguarliu.ai.runtime.RuntimeFailureCategories;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolConfigProperties;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Web GET 工具
 * 获取指定 URL 的网页纯文本内容，适用于内网链接或用户提供的文档链接。
 */
@Slf4j
@Component
public class WebGetTool implements Tool {

    private static final int MAX_CONTENT_LENGTH = 8000;
    private static final int TIMEOUT_MS = 10_000;

    private final ToolConfigProperties toolConfigProperties;

    public WebGetTool(ToolConfigProperties toolConfigProperties) {
        this.toolConfigProperties = toolConfigProperties;
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("web_get")
                .description("获取指定 URL 的网页内容（纯文本）。适用于内网链接或用户提供的文档链接。返回页面主要文字内容，不包含 HTML 标签。内容超长时自动截断。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "url", Map.of(
                                        "type", "string",
                                        "description", "要获取的网页 URL"
                                )
                        ),
                        "required", List.of("url")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        String url = (String) arguments.get("url");
        if (url == null || url.isBlank()) {
            return Mono.just(ToolResult.error("url is required"));
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Mono.just(ToolResult.error("URL 必须使用 http 或 https 协议"));
        }

        // 域名可信检查
        String host;
        try {
            URI uri = URI.create(url);
            host = uri.getHost();
        } catch (Exception e) {
            return Mono.just(ToolResult.error("无效的 URL: " + url));
        }

        if (host == null || !toolConfigProperties.isDomainTrusted(host)) {
            return Mono.just(ToolResult.error(
                    "域名 " + host + " 不在信任列表中，请在设置中添加",
                    RuntimeFailureCategories.USER_DECISION_REQUIRED));
        }

        return Mono.fromCallable(() -> {
            try {
                Document doc = Jsoup.connect(url)
                        .timeout(TIMEOUT_MS)
                        .userAgent("Mozilla/5.0 (compatible; JaguarClaw/1.0)")
                        .get();

                String text = doc.body().text();
                if (text.length() > MAX_CONTENT_LENGTH) {
                    text = text.substring(0, MAX_CONTENT_LENGTH) + "\n...(内容已截断)";
                }

                return ToolResult.success("【页面标题】" + doc.title() + "\n\n【页面内容】\n" + text);
            } catch (Exception e) {
                log.warn("web_get failed for url={}: {}", url, e.getMessage());
                return ToolResult.error("获取网页失败: " + e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
