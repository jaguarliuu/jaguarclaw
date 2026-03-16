package com.jaguarliu.ai.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.runtime.AgentRuntime;
import com.jaguarliu.ai.runtime.CancellationManager;
import com.jaguarliu.ai.runtime.ContextBuilder;
import com.jaguarliu.ai.runtime.LoopConfig;
import com.jaguarliu.ai.runtime.RunContext;
import com.jaguarliu.ai.session.MessageService;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.RunStatus;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.RunEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import com.jaguarliu.ai.tools.integration.DeliveryToolService;
import com.jaguarliu.ai.nodeconsole.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduledTaskExecutor Tests")
class ScheduledTaskExecutorTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private RunService runService;

    @Mock
    private MessageService messageService;

    @Mock
    private AgentRuntime agentRuntime;

    @Mock
    private ContextBuilder contextBuilder;

    @Mock
    private DeliveryToolService deliveryToolService;

    @Mock
    private CancellationManager cancellationManager;

    @Mock
    private LoopConfig loopConfig;

    @Mock
    private ScheduledTaskRepository repository;

    @Mock
    private ScheduleRunLogService runLogService;

    @Mock
    private AuditLogService auditLogService;

    private ScheduledTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ScheduledTaskExecutor(
                sessionService,
                runService,
                messageService,
                agentRuntime,
                contextBuilder,
                deliveryToolService,
                cancellationManager,
                loopConfig,
                repository,
                new ObjectMapper(),
                runLogService,
                auditLogService
        );
    }

    @Test
    @DisplayName("email 定时任务应禁止重复发信并使用无品牌主题")
    void shouldDenySendEmailToolAndUsePlainSubjectForEmailSchedules() throws Exception {
        ScheduledTaskEntity task = ScheduledTaskEntity.builder()
                .id("task-1")
                .name("机器巡检日报")
                .prompt("巡检机器并发送邮件")
                .targetType("email")
                .targetRef("email-default")
                .emailTo("ops@example.com")
                .emailCc("lead@example.com")
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        SessionEntity session = SessionEntity.builder()
                .id("session-1")
                .name("[Scheduled] 机器巡检日报")
                .sessionKind("scheduled")
                .build();
        RunEntity run = RunEntity.builder()
                .id("run-1")
                .sessionId("session-1")
                .status(RunStatus.QUEUED.getValue())
                .prompt(task.getPrompt())
                .build();

        when(sessionService.createScheduledSession(any())).thenReturn(session);
        when(runService.create("session-1", "巡检机器并发送邮件")).thenReturn(run);
        when(contextBuilder.buildMessages(eq(List.of()), any())).thenReturn(List.of(LlmRequest.Message.user("stub")));
        when(agentRuntime.executeLoopWithContext(any(RunContext.class), any(), any()))
                .thenReturn("<h1>巡检报告</h1><p>全部正常</p>");
        when(runLogService.start(any(), any(), any(), any(), any()))
                .thenReturn(ScheduleRunLogEntity.builder().taskId("task-1").taskName("机器巡检日报").status("running").build());

        executor.execute(task);

        ArgumentCaptor<RunContext> contextCaptor = ArgumentCaptor.forClass(RunContext.class);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentRuntime).executeLoopWithContext(contextCaptor.capture(), any(), promptCaptor.capture());
        verify(deliveryToolService, times(1)).sendEmail(
                "ops@example.com",
                "机器巡检日报",
                "<h1>巡检报告</h1><p>全部正常</p>",
                "lead@example.com"
        );

        RunContext context = contextCaptor.getValue();
        assertNotNull(context.getAgentDeniedTools());
        assertEquals(Set.of("send_email"), context.getAgentDeniedTools());
        assertTrue(promptCaptor.getValue().contains("Do not call send_email"));
        assertTrue(promptCaptor.getValue().contains("Return the final email body as clean HTML"));

        // Verify triggeredBy is "scheduled"
        verify(runLogService).start(any(), any(), eq("scheduled"), isNull(), isNull());
        // Verify audit log recorded success
        verify(auditLogService).logScheduleExecution(any(), any(), eq("scheduled"), any(), any(), eq("SUCCESS"), any(), anyLong());
    }

    @Test
    void execute_whenAgentFails_recordsFailureLog() throws Exception {
        // Arrange: make the agent loop throw
        when(agentRuntime.executeLoopWithContext(any(), any(), any()))
                .thenThrow(new RuntimeException("simulated failure"));

        ScheduledTaskEntity task = ScheduledTaskEntity.builder()
                .id("task-fail-1")
                .name("失败任务")
                .prompt("执行失败的任务")
                .targetType(null)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        SessionEntity session = SessionEntity.builder()
                .id("session-fail-1")
                .name("[Scheduled] 失败任务")
                .sessionKind("scheduled")
                .build();
        RunEntity run = RunEntity.builder()
                .id("run-fail-1")
                .sessionId("session-fail-1")
                .status(RunStatus.QUEUED.getValue())
                .prompt(task.getPrompt())
                .build();

        when(sessionService.createScheduledSession(any())).thenReturn(session);
        when(runService.create(any(), any())).thenReturn(run);
        when(contextBuilder.buildMessages(any(), any())).thenReturn(List.of(LlmRequest.Message.user("stub")));
        when(runLogService.start(any(), any(), any(), any(), any()))
                .thenReturn(ScheduleRunLogEntity.builder().taskId("task-fail-1").taskName("失败任务").status("running").build());

        // Act
        executor.execute(task);

        // Assert: run log marked failed
        verify(runLogService).fail(any(ScheduleRunLogEntity.class), eq("simulated failure"));
        // Assert: audit log records FAILED
        verify(auditLogService).logScheduleExecution(
                any(), any(), eq("scheduled"), isNull(), isNull(),
                eq("FAILED"), eq("simulated failure"), anyLong());
        // Assert: task status updated
        assertThat(task.getLastRunSuccess()).isFalse();
        assertThat(task.getLastRunError()).isEqualTo("simulated failure");
    }
}
