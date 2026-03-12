package com.jaguarliu.ai.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduledTaskService Tests")
class ScheduledTaskServiceTest {

    @Mock
    private ScheduledTaskRepository repository;

    @Mock
    private ScheduledTaskExecutor executor;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private ScheduledFuture<Object> firstFuture;

    @Mock
    private ScheduledFuture<Object> secondFuture;

    private ScheduledTaskService service;

    @BeforeEach
    void setUp() {
        service = new ScheduledTaskService(repository, executor, taskScheduler);
    }

    @Test
    @DisplayName("更新任务时应持久化可编辑字段并保留最近执行状态")
    void shouldUpdateEditableFieldsAndPreserveLastRunState() {
        ScheduledTaskEntity existing = existingTask();
        LocalDateTime lastRunAt = LocalDateTime.of(2026, 3, 11, 9, 0);
        existing.setLastRunAt(lastRunAt);
        existing.setLastRunSuccess(false);
        existing.setLastRunError("previous error");

        when(repository.findById("task-1")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(taskScheduler.schedule(any(Runnable.class), any(org.springframework.scheduling.Trigger.class)))
                .thenAnswer(invocation -> secondFuture);

        ScheduledTaskEntity updated = service.update(
                "task-1",
                "Updated name",
                "15 10 * * *",
                "updated prompt",
                "email-default",
                "email",
                "ops@example.com",
                "cc@example.com",
                true
        );

        assertSame(existing, updated);
        assertEquals("Updated name", updated.getName());
        assertEquals("15 10 * * *", updated.getCronExpr());
        assertEquals("updated prompt", updated.getPrompt());
        assertEquals("email-default", updated.getTargetRef());
        assertEquals("email", updated.getTargetType());
        assertEquals("ops@example.com", updated.getEmailTo());
        assertEquals("cc@example.com", updated.getEmailCc());
        assertEquals(lastRunAt, updated.getLastRunAt());
        assertEquals(false, updated.getLastRunSuccess());
        assertEquals("previous error", updated.getLastRunError());
        verify(repository).save(existing);
        verify(taskScheduler).schedule(any(Runnable.class), any(org.springframework.scheduling.Trigger.class));
    }

    @Test
    @DisplayName("更新为禁用时应取消已有调度且不重新注册")
    void shouldCancelExistingScheduleWhenUpdatedDisabled() {
        ScheduledTaskEntity created = createdTask();
        when(repository.save(any(ScheduledTaskEntity.class))).thenReturn(created);
        when(taskScheduler.schedule(any(Runnable.class), any(org.springframework.scheduling.Trigger.class)))
                .thenAnswer(invocation -> firstFuture);

        service.create(
                created.getName(),
                created.getCronExpr(),
                created.getPrompt(),
                created.getTargetRef(),
                created.getTargetType(),
                created.getEmailTo(),
                created.getEmailCc()
        );

        when(repository.findById("task-1")).thenReturn(Optional.of(created));
        when(repository.save(created)).thenReturn(created);

        ScheduledTaskEntity updated = service.update(
                "task-1",
                "Created task",
                "0 9 * * *",
                "daily summary",
                "ops-alert",
                "webhook",
                null,
                null,
                false
        );

        assertFalse(updated.isEnabled());
        verify(firstFuture).cancel(false);
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(org.springframework.scheduling.Trigger.class));
    }

    @Test
    @DisplayName("更新不存在的任务应抛错")
    void shouldThrowWhenUpdatingMissingTask() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.update(
                "missing",
                "Updated name",
                "0 9 * * *",
                "prompt",
                "ops-alert",
                "webhook",
                null,
                null,
                true
        ));

        assertEquals("Scheduled task not found: missing", error.getMessage());
    }

    @Test
    @DisplayName("更新时应校验目标字段")
    void shouldValidateRequiredTargetFieldsOnUpdate() {
        ScheduledTaskEntity existing = existingTask();
        when(repository.findById("task-1")).thenReturn(Optional.of(existing));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.update(
                "task-1",
                "Updated name",
                "0 9 * * *",
                "prompt",
                " ",
                "webhook",
                null,
                null,
                true
        ));

        assertEquals("targetRef is required", error.getMessage());
    }

    @Test
    @DisplayName("创建任务时应先持久化再注册调度")
    void shouldCreateTaskWithGeneratedIdBeforeScheduling() {
        ArgumentCaptor<ScheduledTaskEntity> savedCaptor = ArgumentCaptor.forClass(ScheduledTaskEntity.class);
        when(repository.save(any(ScheduledTaskEntity.class))).thenAnswer(invocation -> {
            ScheduledTaskEntity task = invocation.getArgument(0);
            if (task.getId() == null) {
                task.setId("task-1");
            }
            return task;
        });
        when(taskScheduler.schedule(any(Runnable.class), any(org.springframework.scheduling.Trigger.class)))
                .thenAnswer(invocation -> firstFuture);

        ScheduledTaskEntity created = service.create(
                "Created task",
                "0 9 * * *",
                "daily summary",
                "ops-alert",
                "webhook",
                null,
                null
        );

        assertNotNull(created.getId());
        verify(repository).save(savedCaptor.capture());
        assertEquals("Created task", savedCaptor.getValue().getName());
        verify(taskScheduler).schedule(any(Runnable.class), any(org.springframework.scheduling.Trigger.class));
    }

    private ScheduledTaskEntity existingTask() {
        return ScheduledTaskEntity.builder()
                .id("task-1")
                .name("Existing task")
                .cronExpr("0 9 * * *")
                .prompt("daily summary")
                .targetRef("ops-alert")
                .targetType("webhook")
                .enabled(true)
                .createdAt(LocalDateTime.of(2026, 3, 10, 8, 0))
                .updatedAt(LocalDateTime.of(2026, 3, 10, 8, 0))
                .build();
    }

    private ScheduledTaskEntity createdTask() {
        return ScheduledTaskEntity.builder()
                .id("task-1")
                .name("Created task")
                .cronExpr("0 9 * * *")
                .prompt("daily summary")
                .targetRef("ops-alert")
                .targetType("webhook")
                .enabled(true)
                .createdAt(LocalDateTime.of(2026, 3, 10, 8, 0))
                .updatedAt(LocalDateTime.of(2026, 3, 10, 8, 0))
                .build();
    }
}
