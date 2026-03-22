package com.jaguarliu.ai.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
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
import reactor.core.Disposable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

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
    private final EventBus eventBus;

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
        String sessionId = null;
        String runId = null;
        ScheduleRunTraceCollector traceCollector = null;

        try {
            String executionPrompt = buildExecutionPrompt(task);

            // 1. Create dedicated session
            SessionEntity session = sessionService.createScheduledSession("[Scheduled] " + task.getName());
            sessionId = session.getId();

            // 2. Create run
            RunEntity run = runService.create(session.getId(), task.getPrompt());
            runId = run.getId();
            runService.updateStatus(run.getId(), RunStatus.RUNNING);
            traceCollector = new ScheduleRunTraceCollector(run.getId());
            traceCollector.addSynthetic("run.created", Map.of(
                    "sessionId", session.getId(),
                    "runId", run.getId(),
                    "taskId", task.getId(),
                    "triggeredBy", triggeredBy
            ));

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
            if (traceCollector != null) {
                traceCollector.addSynthetic("run.completed", Map.of(
                        "sessionId", session.getId(),
                        "runId", run.getId(),
                        "status", "success"
                ));
            }
            runLogService.complete(runLog, session.getId(), run.getId(), finishTrace(traceCollector));

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
            if (traceCollector != null) {
                traceCollector.addSynthetic("run.failed", Map.of(
                        "sessionId", sessionId,
                        "runId", runId,
                        "status", "failed",
                        "error", errorMsg
                ));
            }
            runLogService.fail(runLog, errorMsg, sessionId, runId, finishTrace(traceCollector));

            // Write audit log
            long durationMs = System.currentTimeMillis() - startMs;
            auditLogService.logScheduleExecution(
                    task.getId(), task.getName(), triggeredBy,
                    sessionId, runId,
                    "FAILED", errorMsg, durationMs);
        }
    }

    private String finishTrace(ScheduleRunTraceCollector traceCollector) {
        if (traceCollector == null) {
            return null;
        }
        try {
            return traceCollector.finish();
        } catch (Exception e) {
            log.warn("Failed to serialize schedule trace: {}", e.getMessage());
            return null;
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

    private final class ScheduleRunTraceCollector {
        private final List<Map<String, Object>> trace = new CopyOnWriteArrayList<>();
        private final Disposable subscription;

        private ScheduleRunTraceCollector(String runId) {
            this.subscription = eventBus.subscribe(runId)
                    .subscribe(event -> {
                        Map<String, Object> entry = toTraceEntry(event);
                        if (entry != null) {
                            trace.add(entry);
                        }
                    });
        }

        private void addSynthetic(String eventType, Map<String, Object> data) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("eventType", eventType);
            entry.put("timestamp", LocalDateTime.now().toString());
            entry.put("data", data);
            trace.add(entry);
        }

        private String finish() throws Exception {
            subscription.dispose();
            return objectMapper.writeValueAsString(trace);
        }

        private Map<String, Object> toTraceEntry(AgentEvent event) {
            if (event == null || event.getType() == null) {
                return null;
            }
            String eventType = event.getType().getValue();
            if (!isTraceableEvent(eventType)) {
                return null;
            }

            Map<String, Object> data = objectMapper.convertValue(event.getData(), Map.class);
            Map<String, Object> normalized = normalizeTraceData(eventType, data);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("eventType", eventType);
            entry.put("timestamp", LocalDateTime.now().toString());
            entry.put("data", normalized);
            return entry;
        }

        private boolean isTraceableEvent(String eventType) {
            return switch (eventType) {
                case "step.completed",
                     "tool.call",
                     "tool.result",
                     "tool.confirm_request",
                     "skill.activated",
                     "file.created",
                     "subagent.spawned",
                     "subagent.started",
                     "subagent.announced",
                     "subagent.failed",
                     "lifecycle.error",
                     "run.outcome",
                     "context.compacted" -> true;
                default -> false;
            };
        }

        private Map<String, Object> normalizeTraceData(String eventType, Map<String, Object> data) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            if (data != null) {
                normalized.putAll(data);
            }

            if ("tool.result".equals(eventType)) {
                Object content = normalized.get("content");
                if (content instanceof String text) {
                    normalized.put("content", truncate(text, 1600));
                }
            }

            if ("tool.call".equals(eventType) || "tool.confirm_request".equals(eventType)) {
                Object arguments = normalized.get("arguments");
                if (arguments instanceof Map<?, ?> map) {
                    normalized.put("arguments", truncateNestedMap(map));
                }
            }

            return normalized;
        }

        private Map<String, Object> truncateNestedMap(Map<?, ?> map) {
            Map<String, Object> next = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() != null ? entry.getKey().toString() : "null";
                Object value = entry.getValue();
                if (value instanceof String text) {
                    next.put(key, truncate(text, 800));
                } else if (value instanceof Map<?, ?> nested) {
                    next.put(key, truncateNestedMap(nested));
                } else if (value instanceof List<?> list) {
                    next.put(key, truncateList(list));
                } else {
                    next.put(key, value);
                }
            }
            return next;
        }

        private List<Object> truncateList(List<?> list) {
            List<Object> next = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String text) {
                    next.add(truncate(text, 400));
                } else if (item instanceof Map<?, ?> map) {
                    next.add(truncateNestedMap(map));
                } else {
                    next.add(item);
                }
            }
            return next;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
