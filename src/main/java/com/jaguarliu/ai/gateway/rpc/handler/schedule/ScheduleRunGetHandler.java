package com.jaguarliu.ai.gateway.rpc.handler.schedule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.schedule.ScheduleRunLogEntity;
import com.jaguarliu.ai.schedule.ScheduleRunLogService;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.session.SessionFileService;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.MessageEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import com.jaguarliu.ai.storage.entity.SessionFileEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleRunGetHandler implements RpcHandler {

    private final ScheduleRunLogService runLogService;
    private final SessionService sessionService;
    private final MessageService messageService;
    private final SessionFileService sessionFileService;
    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "schedule.runs.get";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            String principalId = resolvePrincipalId(connectionId);
            if (principalId == null) {
                return RpcResponse.error(request.getId(), "UNAUTHORIZED", "Missing authenticated principal");
            }

            String runLogId = extractRunLogId(request.getPayload());
            if (runLogId == null || runLogId.isBlank()) {
                return RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing id");
            }

            ScheduleRunLogEntity runLog = runLogService.get(runLogId)
                    .orElse(null);
            if (runLog == null) {
                return RpcResponse.error(request.getId(), "NOT_FOUND", "Schedule run not found: " + runLogId);
            }

            SessionEntity session = null;
            boolean fallbackScheduledAccess = false;
            if (runLog.getSessionId() != null && !runLog.getSessionId().isBlank()) {
                Optional<SessionEntity> ownedSession = sessionService.get(runLog.getSessionId(), principalId);
                if (ownedSession.isPresent()) {
                    session = ownedSession.get();
                } else {
                    Optional<SessionEntity> scheduledSession = sessionService.get(runLog.getSessionId())
                            .filter(item -> "scheduled".equals(item.getSessionKind()));
                    if (scheduledSession.isPresent()) {
                        session = scheduledSession.get();
                        fallbackScheduledAccess = true;
                    }
                }
                if (session == null) {
                    return RpcResponse.error(request.getId(), "NOT_FOUND", "Session not found: " + runLog.getSessionId());
                }
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("run", ScheduleRunsListHandler.toDto(runLog));
            payload.put("trace", parseTrace(runLog.getTraceJson()));

            if (session != null) {
                List<MessageEntity> messages = fallbackScheduledAccess
                        ? messageService.getSessionHistory(session.getId())
                        : messageService.getSessionHistory(session.getId(), principalId);
                List<SessionFileEntity> files = sessionFileService.listBySession(session.getId());
                payload.put("session", Map.of(
                        "id", session.getId(),
                        "name", session.getName(),
                        "createdAt", session.getCreatedAt().toString(),
                        "updatedAt", session.getUpdatedAt().toString()
                ));
                payload.put("messages", messages.stream().map(this::toMessageDto).toList());
                payload.put("files", files.stream().map(this::toFileDto).toList());
            } else {
                payload.put("session", null);
                payload.put("messages", List.of());
                payload.put("files", List.of());
            }

            return RpcResponse.success(request.getId(), payload);
        }).subscribeOn(Schedulers.boundedElastic()).onErrorResume(e -> {
            log.error("Failed to get schedule run detail: {}", e.getMessage(), e);
            return Mono.just(RpcResponse.error(request.getId(), "GET_FAILED", e.getMessage()));
        });
    }

    private String resolvePrincipalId(String connectionId) {
        var principal = connectionManager.getPrincipal(connectionId);
        return principal != null ? principal.getPrincipalId() : null;
    }

    private String extractRunLogId(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object id = map.get("id");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    private List<Map<String, Object>> parseTrace(String traceJson) {
        if (traceJson == null || traceJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(traceJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse schedule trace json: {}", e.getMessage());
            return List.of(Map.of(
                    "eventType", "trace.parse_error",
                    "timestamp", "",
                    "data", Map.of("error", e.getMessage())
            ));
        }
    }

    private Map<String, Object> toMessageDto(MessageEntity message) {
        return Map.of(
                "id", message.getId(),
                "sessionId", message.getSessionId(),
                "runId", message.getRunId(),
                "role", message.getRole(),
                "content", message.getContent(),
                "payloadJson", message.getPayloadJson() != null ? message.getPayloadJson() : "",
                "createdAt", message.getCreatedAt().toString()
        );
    }

    private Map<String, Object> toFileDto(SessionFileEntity file) {
        return Map.of(
                "id", file.getId(),
                "sessionId", file.getSessionId(),
                "runId", file.getRunId() != null ? file.getRunId() : "",
                "filePath", file.getFilePath(),
                "fileName", file.getFileName(),
                "fileSize", file.getFileSize(),
                "mimeType", file.getMimeType() != null ? file.getMimeType() : "",
                "createdAt", file.getCreatedAt().toString()
        );
    }
}
