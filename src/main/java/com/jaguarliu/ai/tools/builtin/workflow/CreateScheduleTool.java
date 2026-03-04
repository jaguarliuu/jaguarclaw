package com.jaguarliu.ai.tools.builtin.workflow;

import com.jaguarliu.ai.schedule.ScheduledTaskService;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * create_schedule 工具
 * 让 Agent 通过自然语言创建定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateScheduleTool implements Tool {

    private final ScheduledTaskService scheduledTaskService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("create_schedule")
                .description("创建定时执行任务。指定名称、cron 表达式、要执行的 prompt、推送目标类型和目标引用。" +
                        "target_type=email 时需要 email_to；target_type=webhook 时 target_ref 填 webhook 别名。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "name", Map.of(
                                        "type", "string",
                                        "description", "任务名称，简短描述任务用途"
                                ),
                                "cron", Map.of(
                                        "type", "string",
                                        "description", "cron 表达式（5 段格式），如 \"0 9 * * *\" 表示每天9点，\"0 * * * *\" 表示每小时"
                                ),
                                "prompt", Map.of(
                                        "type", "string",
                                        "description", "触发时让 Agent 执行的 prompt 指令"
                                ),
                                "target_type", Map.of(
                                        "type", "string",
                                        "description", "推送目标类型：email 或 webhook"
                                ),
                                "target_ref", Map.of(
                                        "type", "string",
                                        "description", "推送目标引用。email 可省略；webhook 填 webhook 别名"
                                ),
                                "email_to", Map.of(
                                        "type", "string",
                                        "description", "邮箱渠道的收件人地址（仅 email 渠道需要）"
                                ),
                                "email_cc", Map.of(
                                        "type", "string",
                                        "description", "邮箱渠道的抄送地址（可选）"
                                )
                        ),
                        "required", List.of("name", "cron", "prompt")
                ))
                .hitl(true)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        String name = (String) arguments.get("name");
        String cron = (String) arguments.get("cron");
        String prompt = (String) arguments.get("prompt");
        String targetType = asString(arguments.get("target_type"));
        String targetRef = asString(arguments.get("target_ref"));
        String emailTo = (String) arguments.get("email_to");
        String emailCc = (String) arguments.get("email_cc");

        if (name == null || name.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: name"));
        }
        if (cron == null || cron.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: cron"));
        }
        if (prompt == null || prompt.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: prompt"));
        }
        if (targetType == null || targetType.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: target_type (email/webhook)"));
        }

        try {
            targetType = targetType.toLowerCase();
            if (!"email".equals(targetType) && !"webhook".equals(targetType)) {
                return Mono.just(ToolResult.error("target_type must be email or webhook"));
            }

            if ("email".equals(targetType) && (emailTo == null || emailTo.isBlank())) {
                return Mono.just(ToolResult.error("email_to is required for email target"));
            }
            if ("email".equals(targetType) && (targetRef == null || targetRef.isBlank())) {
                targetRef = "email-default";
            }
            if ("webhook".equals(targetType) && (targetRef == null || targetRef.isBlank())) {
                return Mono.just(ToolResult.error("target_ref is required for webhook target (alias)"));
            }

            var task = scheduledTaskService.create(
                    name, cron, prompt,
                    targetRef, targetType,
                    emailTo, emailCc);

            log.info("Schedule created via tool: name={}, cron={}", name, cron);
            return Mono.just(ToolResult.success(
                    "定时任务已创建：\n" +
                    "- 名称: " + task.getName() + "\n" +
                    "- Cron: " + task.getCronExpr() + "\n" +
                    "- 目标: " + task.getTargetType() + " (" + task.getTargetRef() + ")\n" +
                    "- 状态: 已启用"));

        } catch (Exception e) {
            log.error("Failed to create schedule via tool: {}", e.getMessage());
            return Mono.just(ToolResult.error("创建定时任务失败: " + e.getMessage()));
        }
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String str = value.toString().trim();
        return str.isEmpty() ? null : str;
    }
}
