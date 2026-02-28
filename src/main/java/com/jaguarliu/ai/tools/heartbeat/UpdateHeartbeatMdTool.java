package com.jaguarliu.ai.tools.heartbeat;

import com.jaguarliu.ai.heartbeat.HeartbeatConfigService;
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
public class UpdateHeartbeatMdTool implements Tool {

    private final HeartbeatConfigService heartbeatConfigService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("update_heartbeat_md")
                .description("Update the HEARTBEAT.md checklist. Use this to add reminders, tracking items, or adjust what the heartbeat checks. Provide the complete new content of the file.")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "content", Map.of("type", "string",
                                        "description", "The complete new content of HEARTBEAT.md (Markdown format). Must include the instruction to reply HEARTBEAT_OK when nothing to report."),
                                "reason", Map.of("type", "string",
                                        "description", "Why you are updating the heartbeat checklist")
                        ),
                        "required", List.of("content")
                ))
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args) {
        String content = args != null ? (String) args.get("content") : null;
        String reason = args != null ? (String) args.get("reason") : "no reason provided";

        if (content == null || content.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: content"));
        }
        if (reason == null || reason.isBlank()) {
            reason = "no reason provided";
        }

        try {
            heartbeatConfigService.writeHeartbeatMd(content);
            String msg = String.format("HEARTBEAT.md updated. Reason: %s. Changes will take effect on the next heartbeat cycle.", reason);
            return Mono.just(ToolResult.success(msg));
        } catch (Exception e) {
            log.error("Failed to update HEARTBEAT.md", e);
            return Mono.just(ToolResult.error("Failed to update HEARTBEAT.md: " + e.getMessage()));
        }
    }
}
