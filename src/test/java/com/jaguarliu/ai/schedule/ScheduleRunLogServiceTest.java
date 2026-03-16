package com.jaguarliu.ai.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ScheduleRunLogServiceTest {

    private ScheduleRunLogRepository repo;
    private ScheduleRunLogService service;

    @BeforeEach
    void setUp() {
        repo = mock(ScheduleRunLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new ScheduleRunLogService(repo);
    }

    @Test
    void startLog_createsRunningEntry() {
        var log = service.start("task-1", "Daily Report", "manual", null, null);

        assertThat(log.getStatus()).isEqualTo("running");
        assertThat(log.getTaskId()).isEqualTo("task-1");
        assertThat(log.getTriggeredBy()).isEqualTo("manual");
        assertThat(log.getStartedAt()).isNotNull();
        assertThat(log.getTaskName()).isEqualTo("Daily Report");
        verify(repo).save(log);
    }

    @Test
    void completeLog_updatesStatusAndDuration() {
        var log = ScheduleRunLogEntity.builder()
                .id("log-1").taskId("t1").taskName("T").triggeredBy("scheduled")
                .status("running").startedAt(LocalDateTime.now().minusSeconds(5))
                .build();

        service.complete(log, "session-1", "run-1");

        assertThat(log.getStatus()).isEqualTo("success");
        assertThat(log.getFinishedAt()).isNotNull();
        assertThat(log.getDurationMs()).isGreaterThan(0);
        assertThat(log.getSessionId()).isEqualTo("session-1");
        assertThat(log.getRunId()).isEqualTo("run-1");
        verify(repo).save(log);
    }

    @Test
    void failLog_updatesStatusAndError() {
        var log = ScheduleRunLogEntity.builder()
                .id("log-1").taskId("t1").taskName("T").triggeredBy("scheduled")
                .status("running").startedAt(LocalDateTime.now().minusSeconds(1))
                .build();

        service.fail(log, "Connection refused");

        assertThat(log.getStatus()).isEqualTo("failed");
        assertThat(log.getErrorMessage()).isEqualTo("Connection refused");
        assertThat(log.getFinishedAt()).isNotNull();
        assertThat(log.getDurationMs()).isGreaterThan(0);
        verify(repo).save(log);
    }
}
