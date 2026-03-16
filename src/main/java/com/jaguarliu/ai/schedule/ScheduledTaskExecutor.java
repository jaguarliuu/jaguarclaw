package com.jaguarliu.ai.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.nodeconsole.AuditLogService;
import com.jaguarliu.ai.runtime.*;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.RunStatus;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import com.jaguarliu.ai.tools.integration.DeliveryToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定时任务执行器
 * 创建无头 Agent 会话，执行 prompt，并将结果推送到指定渠道
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTaskExecutor {

    private static final String EMAIL_AUTO_DELIVERY_INSTRUCTION = """

            IMPORTANT: This scheduled task already has automatic email delivery configured.
            Do not call send_email yourself.
            Return only the final email body content.
            Return the final email body as clean HTML using tags like <h1>, <h2>, <p>, <ul>, <ol>, <li>, <table>, <tr>, <th>, <td> when helpful.
            Do not wrap the result in Markdown fences.
            Do not add meta commentary about sending email.
            """;

    private static final String WEBHOOK_AUTO_DELIVERY_INSTRUCTION = """

            IMPORTANT: This scheduled task already has automatic webhook delivery configured.
            Do not call send_webhook yourself.
            Return only the final payload content to be delivered.
            Do not add meta commentary about sending the webhook.
            """;

    private final SessionService sessionService;
    private final RunService runService;
    private final MessageService messageService;
    private final AgentRuntime agentRuntime;
    private final ContextBuilder contextBuilder;
    private final DeliveryToolService deliveryToolService;
    private final CancellationManager cancellationManager;
    private final LoopConfig loopConfig;
    private final ScheduledTaskRepository repository;
    private final ObjectMapper objectMapper;
    private final ScheduleRunLogService runLogService;
    private final AuditLogService auditLogService;

    /**
     * 执行定时任务
     */
    public void execute(ScheduledTaskEntity task) {
        execute(task, "scheduled");
    }

    public void execute(ScheduledTaskEntity task, String triggeredBy) {
        log.info("Scheduled task executing: name={}, id={}, triggeredBy={}", task.getName(), task.getId(), triggeredBy);
        long startMs = System.currentTimeMillis();

        // Write RUNNING record immediately
        ScheduleRunLogEntity runLog = runLogService.start(task.getId(), task.getName(), triggeredBy, null, null);

        try {
            String executionPrompt = buildExecutionPrompt(task);

            // 1. Create dedicated session
            SessionEntity session = sessionService.createScheduledSession("[Scheduled] " + task.getName());

            // 2. Create run
            RunEntity run = runService.create(session.getId(), task.getPrompt());
            runService.updateStatus(run.getId(), RunStatus.RUNNING);

            // 3. Save user message
            messageService.saveUserMessage(session.getId(), run.getId(), task.getPrompt());

            // 4. Build message context (no history)
            List<LlmRequest.Message> messages = contextBuilder.buildMessages(List.of(), executionPrompt);

            // 5. Build scheduled RunContext (runKind = "scheduled", skip HITL)
            RunContext context = RunContext.createScheduled(
                    run.getId(), session.getId(), loopConfig, cancellationManager);
            context.setAgentDeniedTools(resolveAutoDeliveryDeniedTools(task));

            // 6. Execute agent loop
            String response = agentRuntime.executeLoopWithContext(context, messages, executionPrompt);

            // 7. Save assistant message
            messageService.saveAssistantMessage(session.getId(), run.getId(), response);
            runService.updateStatus(run.getId(), RunStatus.DONE);

            // 8. Push result to target
            pushToDeliveryTarget(task, response, true, null);

            // 9. Update task status
            task.setLastRunAt(LocalDateTime.now());
            task.setLastRunSuccess(true);
            task.setLastRunError(null);
            repository.save(task);

            // 10. Complete run log
            runLogService.complete(runLog, session.getId(), run.getId());

            // 11. Write audit log
            long durationMs = System.currentTimeMillis() - startMs;
            auditLogService.logScheduleExecution(
                    task.getId(), task.getName(), triggeredBy,
                    session.getId(), run.getId(),
                    "SUCCESS", "Task completed successfully", durationMs);

            log.info("Scheduled task completed: name={}, id={}", task.getName(), task.getId());

        } catch (Exception e) {
            log.error("Scheduled task failed: name={}, error={}", task.getName(), e.getMessage(), e);

            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();

            // Push error to target
            pushToDeliveryTarget(task, null, false, errorMsg);

            // Update task status
            task.setLastRunAt(LocalDateTime.now());
            task.setLastRunSuccess(false);
            task.setLastRunError(errorMsg);
            repository.save(task);

            // Mark run log as failed
            runLogService.fail(runLog, errorMsg);

            // Write audit log
            long durationMs = System.currentTimeMillis() - startMs;
            auditLogService.logScheduleExecution(
                    task.getId(), task.getName(), triggeredBy,
                    null, null,
                    "FAILED", errorMsg, durationMs);
        }
    }

    private void pushToDeliveryTarget(ScheduledTaskEntity task, String result, boolean success, String error) {
        try {
            if ("email".equals(task.getTargetType())) {
                String subject = success
                        ? task.getName()
                        : task.getName() + " - 执行失败";
                String body = success ? result : "任务执行失败：\n" + error;
                deliveryToolService.sendEmail(task.getEmailTo(), subject, body, task.getEmailCc());
            } else if ("webhook".equals(task.getTargetType())) {
                Map<String, Object> payload = Map.of(
                        "task", task.getName(),
                        "success", success,
                        "content", success ? result : error,
                        "timestamp", LocalDateTime.now().toString()
                );
                deliveryToolService.sendWebhook(task.getTargetRef(), objectMapper.writeValueAsString(payload), null);
            }
        } catch (Exception e) {
            log.error("Failed to push scheduled task result to delivery target: {}", e.getMessage());
        }
    }

    private String buildExecutionPrompt(ScheduledTaskEntity task) {
        if ("email".equals(task.getTargetType())) {
            return task.getPrompt() + EMAIL_AUTO_DELIVERY_INSTRUCTION;
        }
        if ("webhook".equals(task.getTargetType())) {
            return task.getPrompt() + WEBHOOK_AUTO_DELIVERY_INSTRUCTION;
        }
        return task.getPrompt();
    }

    private Set<String> resolveAutoDeliveryDeniedTools(ScheduledTaskEntity task) {
        if ("email".equals(task.getTargetType())) {
            return Set.of("send_email");
        }
        if ("webhook".equals(task.getTargetType())) {
            return Set.of("send_webhook");
        }
        return Set.of();
    }
}
