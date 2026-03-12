package com.jaguarliu.ai.gateway.rpc.handler.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.schedule.ScheduledTaskEntity;
import com.jaguarliu.ai.schedule.ScheduledTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleUpdateHandler Tests")
class ScheduleUpdateHandlerTest {

    @Mock
    private ScheduledTaskService scheduledTaskService;

    private ScheduleUpdateHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ScheduleUpdateHandler(scheduledTaskService, new ObjectMapper());
    }

    @Test
    @DisplayName("schedule.update 成功时返回最新 DTO")
    void shouldReturnUpdatedScheduleDto() {
        ScheduledTaskEntity entity = ScheduledTaskEntity.builder()
                .id("task-1")
                .name("Updated task")
                .cronExpr("15 10 * * *")
                .prompt("updated prompt")
                .targetRef("email-default")
                .targetType("email")
                .emailTo("ops@example.com")
                .emailCc("cc@example.com")
                .enabled(true)
                .createdAt(LocalDateTime.of(2026, 3, 10, 8, 0))
                .updatedAt(LocalDateTime.of(2026, 3, 12, 9, 0))
                .build();
        when(scheduledTaskService.update(
                eq("task-1"),
                eq("Updated task"),
                eq("15 10 * * *"),
                eq("updated prompt"),
                eq("email-default"),
                eq("email"),
                eq("ops@example.com"),
                eq("cc@example.com"),
                eq(true)
        )).thenReturn(entity);

        RpcRequest request = RpcRequest.builder()
                .id("req-1")
                .method("schedule.update")
                .payload(Map.of(
                        "id", "task-1",
                        "name", "Updated task",
                        "cronExpr", "15 10 * * *",
                        "prompt", "updated prompt",
                        "targetRef", "email-default",
                        "targetType", "email",
                        "emailTo", "ops@example.com",
                        "emailCc", "cc@example.com",
                        "enabled", true
                ))
                .build();

        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        assertNull(response.getError());
        Map<?, ?> payload = (Map<?, ?>) response.getPayload();
        assertEquals("task-1", payload.get("id"));
        assertEquals("Updated task", payload.get("name"));
        assertEquals("15 10 * * *", payload.get("cronExpr"));
        assertEquals(true, payload.get("enabled"));
    }

    @Test
    @DisplayName("缺少必填参数时返回 INVALID_PARAMS")
    void shouldRejectMissingRequiredParameters() {
        RpcRequest request = RpcRequest.builder()
                .id("req-2")
                .method("schedule.update")
                .payload(Map.of(
                        "id", "task-1",
                        "cronExpr", "15 10 * * *",
                        "prompt", "updated prompt",
                        "targetRef", "ops-alert",
                        "targetType", "webhook",
                        "enabled", true
                ))
                .build();

        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals("INVALID_PARAMS", response.getError().getCode());
        assertEquals("name is required", response.getError().getMessage());
        verifyNoInteractions(scheduledTaskService);
    }
}
