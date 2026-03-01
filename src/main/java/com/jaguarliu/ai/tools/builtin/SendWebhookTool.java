package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.integration.DeliveryToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendWebhookTool implements Tool {

    private final DeliveryToolService deliveryToolService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("send_webhook")
                .description("""
                        Webhook 工具（支持发送、保存、列出、删除 webhook 目标）。
                        - action=send: 按 target 别名触发 webhook（payload 必填）
                        - action=save: 保存/更新 webhook 地址与触发机制（alias,url 必填）
                        - action=list: 列出全部 webhook 目标
                        - action=remove: 删除 webhook 目标（alias 必填）
                        """)
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "action", Map.of(
                                        "type", "string",
                                        "description", "动作类型：send/save/list/remove，默认 send"
                                ),
                                "target", Map.of(
                                        "type", "string",
                                        "description", "send 动作使用的目标别名/URL（可选，不传默认使用第一个启用目标）"
                                ),
                                "payload", Map.of(
                                        "type", "string",
                                        "description", "send 动作的请求体（建议 JSON 字符串）"
                                ),
                                "alias", Map.of(
                                        "type", "string",
                                        "description", "save/remove 动作的 webhook 别名"
                                ),
                                "url", Map.of(
                                        "type", "string",
                                        "description", "save 动作的 webhook URL"
                                ),
                                "method", Map.of(
                                        "type", "string",
                                        "description", "save 动作的 HTTP 方法（POST/PUT，默认 POST）"
                                ),
                                "headers", Map.of(
                                        "type", "object",
                                        "description", "save 动作的请求头（可选）"
                                ),
                                "trigger", Map.of(
                                        "type", "string",
                                        "description", "send/save 的触发机制说明（如：日报、故障告警）"
                                ),
                                "enabled", Map.of(
                                        "type", "boolean",
                                        "description", "save 动作时是否启用该 endpoint（默认 true）"
                                )
                        )
                ))
                .hitl(true)
                .build();
    }

    @Override
    public boolean isEnabled() {
        return deliveryToolService.isWebhookToolActivated();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String action = value(arguments.get("action"), "send").toLowerCase();
            String target = value(arguments.get("target"), null);
            String payload = value(arguments.get("payload"), null);
            String alias = value(arguments.get("alias"), null);
            String url = value(arguments.get("url"), null);
            String method = value(arguments.get("method"), null);
            String trigger = value(arguments.get("trigger"), null);
            Boolean enabled = arguments.get("enabled") instanceof Boolean b ? b : null;

            @SuppressWarnings("unchecked")
            Map<String, Object> rawHeaders = arguments.get("headers") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map : Map.of();
            Map<String, String> headers = new LinkedHashMap<>();
            for (Entry<String, Object> entry : rawHeaders.entrySet()) {
                if (entry.getValue() != null) {
                    headers.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }

            return switch (action) {
                case "list" -> ToolResult.success(formatWebhookList());
                case "remove" -> {
                    if (alias == null || alias.isBlank()) {
                        yield ToolResult.error("alias is required for action=remove");
                    }
                    yield ToolResult.success(deliveryToolService.removeWebhook(alias));
                }
                case "save" -> {
                    if (alias == null || alias.isBlank()) {
                        yield ToolResult.error("alias is required for action=save");
                    }
                    if (url == null || url.isBlank()) {
                        yield ToolResult.error("url is required for action=save");
                    }
                    yield ToolResult.success(deliveryToolService.upsertWebhook(
                            alias, url, method, headers, trigger, enabled));
                }
                case "send" -> {
                    if (payload == null || payload.isBlank()) {
                        yield ToolResult.error("payload is required for action=send");
                    }
                    yield ToolResult.success(deliveryToolService.sendWebhook(target, payload, trigger));
                }
                default -> ToolResult.error("Unsupported action: " + action + ". Use send/save/list/remove");
            };
        }).onErrorResume(e -> {
            log.error("send_webhook failed: {}", e.getMessage());
            return Mono.just(ToolResult.error(e.getMessage()));
        });
    }

    private String formatWebhookList() {
        var endpoints = deliveryToolService.listWebhooks();
        if (endpoints.isEmpty()) {
            return "No webhook endpoints configured yet.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Configured webhook endpoints:\n");
        for (Map<String, Object> ep : endpoints) {
            sb.append("- ")
                    .append(ep.getOrDefault("alias", ""))
                    .append(" [")
                    .append(ep.getOrDefault("method", "POST"))
                    .append("] ")
                    .append(ep.getOrDefault("url", ""))
                    .append(" (enabled=")
                    .append(ep.getOrDefault("enabled", true))
                    .append(")");
            Object trigger = ep.get("trigger");
            if (trigger != null && !String.valueOf(trigger).isBlank()) {
                sb.append(" trigger=").append(trigger);
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String value(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String str = String.valueOf(value).trim();
        return str.isEmpty() ? defaultValue : str;
    }
}
