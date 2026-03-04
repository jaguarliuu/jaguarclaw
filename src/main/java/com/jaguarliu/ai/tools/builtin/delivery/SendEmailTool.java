package com.jaguarliu.ai.tools.builtin.delivery;

import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.integration.DeliveryToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendEmailTool implements Tool {

    private final DeliveryToolService deliveryToolService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("send_email")
                .description("发送邮件（前提：邮件工具已在设置中启用并配置 SMTP）。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "to", Map.of(
                                        "type", "string",
                                        "description", "收件人地址（逗号分隔多个）"
                                ),
                                "subject", Map.of(
                                        "type", "string",
                                        "description", "邮件主题"
                                ),
                                "body", Map.of(
                                        "type", "string",
                                        "description", "邮件正文，支持 HTML 格式（如 <h1>、<p>、<table> 等）。纯文本也可以，换行会自动转为 <br>"
                                ),
                                "cc", Map.of(
                                        "type", "string",
                                        "description", "抄送地址（逗号分隔多个，可选）"
                                )
                        ),
                        "required", List.of("to", "subject", "body")
                ))
                .hitl(true)
                .build();
    }

    @Override
    public boolean isEnabled() {
        return deliveryToolService.isEmailToolEnabled();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String to = (String) arguments.get("to");
            String subject = (String) arguments.get("subject");
            String body = (String) arguments.get("body");
            String cc = (String) arguments.get("cc");

            if (to == null || to.isBlank()) {
                return ToolResult.error("Missing required parameter: to");
            }
            if (subject == null || subject.isBlank()) {
                return ToolResult.error("Missing required parameter: subject");
            }
            if (body == null || body.isBlank()) {
                return ToolResult.error("Missing required parameter: body");
            }

            String result = deliveryToolService.sendEmail(to, subject, body, cc);
            return ToolResult.success(result);
        }).onErrorResume(e -> {
            log.error("send_email failed: {}", e.getMessage());
            return Mono.just(ToolResult.error(e.getMessage()));
        });
    }
}
