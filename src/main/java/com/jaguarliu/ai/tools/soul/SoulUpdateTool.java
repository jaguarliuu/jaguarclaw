package com.jaguarliu.ai.tools.soul;

import com.jaguarliu.ai.soul.SoulConfigService;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SoulUpdateTool implements Tool {

    private final SoulConfigService soulConfigService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("update_soul")
                .description("Update your own identity/preferences.")
                .parameters(Map.of("type", "object"))
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args) {
        String field = args != null ? (String) args.get("field") : null;
        String value = args != null ? (String) args.get("value") : null;
        String reason = args != null ? (String) args.get("reason") : null;

        if (field == null || field.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: field"));
        }
        if (value == null) {
            return Mono.just(ToolResult.error("Missing required parameter: value"));
        }
        if (reason == null || reason.isBlank()) {
            reason = "no reason provided";
        }

        try {
            java.util.Map<String, Object> cfg = new java.util.LinkedHashMap<>(soulConfigService.getConfig());
            Object parsed = parseValueForField(field, value);
            cfg.put(field, parsed);
            soulConfigService.saveConfig(cfg);

            // UI can refresh on tool.result for update_soul; explicit event not required

            String msg = String.format("Updated %s. Reason: %s. This change will affect future interactions.", field, reason);
            return Mono.just(ToolResult.success(msg));
        } catch (Exception e) {
            log.error("Failed to update soul", e);
            return Mono.just(ToolResult.error("Failed to update: " + e.getMessage()));
        }
    }

    private Object parseValueForField(String field, String value) {
        if (field == null) return value;
        switch (field) {
            case "traits":
            case "expertise":
            case "forbiddenTopics":
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    return mapper.readValue(value, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>(){});
                } catch (Exception e) {
                    // Fallback to empty list if parse fails
                    return java.util.Collections.emptyList();
                }
            default:
                return value;
        }
    }
}
