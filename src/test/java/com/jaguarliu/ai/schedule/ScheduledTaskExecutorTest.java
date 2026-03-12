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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
                new ObjectMapper()
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
    }
}
