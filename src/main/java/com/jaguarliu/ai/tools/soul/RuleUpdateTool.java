package com.jaguarliu.ai.tools.soul;

import com.jaguarliu.ai.soul.SoulConfigService;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleUpdateTool implements Tool {

    private final SoulConfigService soulConfigService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("update_rule")
                .description("Rewrite the RULE.md to update your behavioral constraints.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "content", Map.of("type", "string",
                                        "description", "Full Markdown content to write to RULE.md"),
                                "reason", Map.of("type", "string",
                                        "description", "Why you are making this update")
                        ),
                        "required", List.of("content")
                ))
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args) {
        String content = args != null ? (String) args.get("content") : null;
        String reason = args != null ? (String) args.get("reason") : "no reason provided";

        if (content == null) {
            return Mono.just(ToolResult.error("Missing required parameter: content"));
        }
        if (reason == null || reason.isBlank()) {
            reason = "no reason provided";
        }

        try {
            soulConfigService.writeRuleMd("main", content);
            String msg = String.format("RULE.md updated. Reason: %s. This change will affect future interactions.", reason);
            return Mono.just(ToolResult.success(msg));
        } catch (Exception e) {
            log.error("Failed to update RULE.md", e);
            return Mono.just(ToolResult.error("Failed to update: " + e.getMessage()));
        }
    }
}
