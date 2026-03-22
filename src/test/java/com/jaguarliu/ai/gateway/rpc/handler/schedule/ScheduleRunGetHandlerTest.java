package com.jaguarliu.ai.gateway.rpc.handler.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.security.ConnectionPrincipal;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.schedule.ScheduleRunLogEntity;
import com.jaguarliu.ai.schedule.ScheduleRunLogService;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.session.SessionFileService;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.MessageEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import com.jaguarliu.ai.storage.entity.SessionFileEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleRunGetHandler Tests")
class ScheduleRunGetHandlerTest {

    @Mock
    private ScheduleRunLogService runLogService;

    @Mock
    private SessionService sessionService;

    @Mock
    private MessageService messageService;

    @Mock
    private SessionFileService sessionFileService;

    @Mock
    private ConnectionManager connectionManager;

    private ScheduleRunGetHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ScheduleRunGetHandler(
                runLogService,
                sessionService,
                messageService,
                sessionFileService,
                connectionManager,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("schedule.runs.get 成功时返回 trace 与消息")
    void shouldReturnRunDetail() {
        when(connectionManager.getPrincipal("conn-1")).thenReturn(ConnectionPrincipal.builder()
                .principalId("local-default")
                .build());

        ScheduleRunLogEntity runLog = ScheduleRunLogEntity.builder()
                .id("log-1")
                .taskId("task-1")
                .taskName("Daily Check")
                .triggeredBy("manual")
                .status("success")
                .startedAt(LocalDateTime.of(2026, 3, 20, 9, 0))
                .finishedAt(LocalDateTime.of(2026, 3, 20, 9, 1))
                .durationMs(60_000)
                .sessionId("session-1")
                .runId("run-1")
                .traceJson("""
                        [{"eventType":"tool.call","timestamp":"2026-03-20T09:00:10","data":{"toolName":"web_get"}}]
                        """)
                .build();
        when(runLogService.get("log-1")).thenReturn(Optional.of(runLog));

        SessionEntity session = SessionEntity.builder()
                .id("session-1")
                .name("[Scheduled] Daily Check")
                .createdAt(LocalDateTime.of(2026, 3, 20, 9, 0))
                .updatedAt(LocalDateTime.of(2026, 3, 20, 9, 1))
                .build();
        when(sessionService.get("session-1", "local-default")).thenReturn(Optional.of(session));

        when(messageService.getSessionHistory("session-1", "local-default")).thenReturn(List.of(
                MessageEntity.builder()
                        .id("msg-1")
                        .sessionId("session-1")
                        .runId("run-1")
                        .role("user")
                        .content("check cluster")
                        .createdAt(LocalDateTime.of(2026, 3, 20, 9, 0))
                        .build()
        ));
        when(sessionFileService.listBySession("session-1")).thenReturn(List.of(
                SessionFileEntity.builder()
                        .id("file-1")
                        .sessionId("session-1")
                        .runId("run-1")
                        .filePath("reports/daily.html")
                        .fileName("daily.html")
                        .fileSize(128L)
                        .createdAt(LocalDateTime.of(2026, 3, 20, 9, 1))
                        .build()
        ));

        RpcRequest request = RpcRequest.builder()
                .id("req-1")
                .method("schedule.runs.get")
                .payload(Map.of("id", "log-1"))
                .build();

        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        assertNull(response.getError());
        Map<?, ?> payload = (Map<?, ?>) response.getPayload();
        Map<?, ?> run = (Map<?, ?>) payload.get("run");
        assertEquals("log-1", run.get("id"));
        List<?> trace = (List<?>) payload.get("trace");
        assertEquals(1, trace.size());
        Map<?, ?> firstTrace = (Map<?, ?>) trace.get(0);
        assertEquals("tool.call", firstTrace.get("eventType"));
        List<?> messages = (List<?>) payload.get("messages");
        assertEquals(1, messages.size());
        List<?> files = (List<?>) payload.get("files");
        assertEquals(1, files.size());
    }

    @Test
    @DisplayName("scheduled session owner 不匹配时仍允许查看详情")
    void shouldFallbackToScheduledSessionLookup() {
        when(connectionManager.getPrincipal("conn-2")).thenReturn(ConnectionPrincipal.builder()
                .principalId("device-xyz")
                .build());

        ScheduleRunLogEntity runLog = ScheduleRunLogEntity.builder()
                .id("log-2")
                .taskId("task-2")
                .taskName("Nightly Job")
                .triggeredBy("scheduled")
                .status("success")
                .startedAt(LocalDateTime.of(2026, 3, 20, 22, 0))
                .sessionId("session-2")
                .runId("run-2")
                .build();
        when(runLogService.get("log-2")).thenReturn(Optional.of(runLog));
        when(sessionService.get("session-2", "device-xyz")).thenReturn(Optional.empty());
        when(sessionService.get("session-2")).thenReturn(Optional.of(SessionEntity.builder()
                .id("session-2")
                .name("[Scheduled] Nightly Job")
                .sessionKind("scheduled")
                .createdAt(LocalDateTime.of(2026, 3, 20, 22, 0))
                .updatedAt(LocalDateTime.of(2026, 3, 20, 22, 1))
                .build()));
        when(messageService.getSessionHistory("session-2")).thenReturn(List.of(
                MessageEntity.builder()
                        .id("msg-2")
                        .sessionId("session-2")
                        .runId("run-2")
                        .role("assistant")
                        .content("done")
                        .createdAt(LocalDateTime.of(2026, 3, 20, 22, 1))
                        .build()
        ));
        when(sessionFileService.listBySession("session-2")).thenReturn(List.of());

        RpcRequest request = RpcRequest.builder()
                .id("req-2")
                .method("schedule.runs.get")
                .payload(Map.of("id", "log-2"))
                .build();

        RpcResponse response = handler.handle("conn-2", request).block();

        assertNotNull(response);
        assertNull(response.getError());
        Map<?, ?> payload = (Map<?, ?>) response.getPayload();
        Map<?, ?> session = (Map<?, ?>) payload.get("session");
        assertEquals("session-2", session.get("id"));
        List<?> messages = (List<?>) payload.get("messages");
        assertEquals(1, messages.size());
    }
}
