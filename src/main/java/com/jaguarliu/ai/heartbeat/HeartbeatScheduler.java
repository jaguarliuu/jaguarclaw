package com.jaguarliu.ai.heartbeat;

import com.jaguarliu.ai.agents.service.AgentProfileService;
import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.runtime.AgentRuntime;
import com.jaguarliu.ai.runtime.CancellationManager;
import com.jaguarliu.ai.runtime.ContextBuilder;
import com.jaguarliu.ai.runtime.LoopConfig;
import com.jaguarliu.ai.runtime.RunContext;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Heartbeat 定时调度器
 * 每分钟检查一次，按配置的间隔、时间窗口触发 Agent 执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatScheduler {

    private final HeartbeatConfigService configService;
    private final SessionService sessionService;
    private final RunService runService;
    private final ContextBuilder contextBuilder;
    private final AgentRuntime agentRuntime;
    private final ConnectionManager connectionManager;
    private final EventBus eventBus;
    private final LoopConfig loopConfig;
    private final CancellationManager cancellationManager;
    private final AgentProfileService agentProfileService;

    private final Map<String, AtomicLong> lastRunAtByAgent = new ConcurrentHashMap<>();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        try {
            List<String> targetAgentIds = Stream.concat(
                            Stream.of("main"),
                            agentProfileService.list().stream()
                                    .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                                    .map(p -> p.getId())
                    )
                    .distinct()
                    .toList();

            for (String agentId : targetAgentIds) {
                tickForAgent(agentId);
            }

        } catch (Exception e) {
            log.error("Heartbeat tick failed", e);
        }
    }

    private void tickForAgent(String agentId) {
        try {
            Map<String, Object> config = configService.getConfig(agentId);

            // 1. Check enabled
            if (!Boolean.TRUE.equals(config.get("enabled"))) {
                return;
            }

            // 2. Check interval
            int intervalMinutes = ((Number) config.getOrDefault("intervalMinutes", 30)).intValue();
            long now = System.currentTimeMillis();
            long intervalMs = (long) intervalMinutes * 60 * 1000;
            AtomicLong lastRunAt = lastRunAtByAgent.computeIfAbsent(agentId, ignored -> new AtomicLong(0));
            if (now - lastRunAt.get() < intervalMs) {
                return;
            }

            // 3. Check active hours
            String timezone = (String) config.getOrDefault("timezone", "Asia/Shanghai");
            String startStr = (String) config.getOrDefault("activeHoursStart", "09:00");
            String endStr = (String) config.getOrDefault("activeHoursEnd", "22:00");
            ZonedDateTime zonedNow = ZonedDateTime.now(ZoneId.of(timezone));
            LocalTime currentTime = zonedNow.toLocalTime();
            LocalTime startTime = LocalTime.parse(startStr, TIME_FMT);
            LocalTime endTime = LocalTime.parse(endStr, TIME_FMT);
            if (currentTime.isBefore(startTime) || currentTime.isAfter(endTime)) {
                return;
            }

            log.info("Heartbeat tick starting for agentId={}...", agentId);
            lastRunAt.set(now);

            // 4. Read HEARTBEAT.md
            String prompt = configService.readHeartbeatMd(agentId);

            // 5. Create session + run
            SessionEntity session = sessionService.createScheduledSession("[Heartbeat][" + agentId + "]");
            RunEntity run = runService.create(session.getId(), prompt, agentId);

            // 6. Build messages + context
            List<LlmRequest.Message> messages = contextBuilder.build(prompt).getMessages();
            RunContext context = RunContext.createScheduled(
                    run.getId(), session.getId(), agentId, loopConfig, cancellationManager
            );
            cancellationManager.register(run.getId());

            // 7. Execute
            String response = agentRuntime.executeLoopWithContext(context, messages, prompt);
            log.info("Heartbeat execution completed: agentId={}, response length={}", agentId, response.length());

            // 8. Silent ack check
            int ackMaxChars = ((Number) config.getOrDefault("ackMaxChars", 300)).intValue();
            String trimmed = response.trim();
            if (trimmed.startsWith("HEARTBEAT_OK") && trimmed.length() <= ackMaxChars) {
                log.info("Heartbeat returned silent ack, discarding: agentId={}", agentId);
                return;
            }

            // 9. Broadcast to all connections
            broadcastHeartbeat(trimmed, session.getId(), run.getId());
        } catch (Exception e) {
            log.error("Heartbeat tick failed for agentId={}", agentId, e);
        }
    }

    private void broadcastHeartbeat(String content, String sessionId, String runId) {
        connectionManager.getAllConnectionIds().forEach(connId ->
                eventBus.publish(AgentEvent.heartbeatNotify(connId, content, sessionId, runId))
        );
        log.info("Heartbeat broadcast to {} connections", connectionManager.getConnectionCount());
    }
}
