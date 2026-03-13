package com.jaguarliu.ai.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleRunLogService {

    private final ScheduleRunLogRepository repository;

    public ScheduleRunLogEntity start(String taskId, String taskName,
                                      String triggeredBy,
                                      String sessionId, String runId) {
        ScheduleRunLogEntity entry = ScheduleRunLogEntity.builder()
                .taskId(taskId)
                .taskName(taskName)
                .triggeredBy(triggeredBy)
                .status("running")
                .startedAt(LocalDateTime.now())
                .sessionId(sessionId)
                .runId(runId)
                .build();
        ScheduleRunLogEntity saved = repository.save(entry);
        log.debug("Schedule run log started: taskId={}, triggeredBy={}", taskId, triggeredBy);
        return saved;
    }

    public void complete(ScheduleRunLogEntity entry, String sessionId, String runId) {
        LocalDateTime now = LocalDateTime.now();
        entry.setStatus("success");
        entry.setFinishedAt(now);
        entry.setDurationMs((int) ChronoUnit.MILLIS.between(entry.getStartedAt(), now));
        if (sessionId != null) entry.setSessionId(sessionId);
        if (runId != null) entry.setRunId(runId);
        repository.save(entry);
        log.debug("Schedule run log completed: id={}, durationMs={}", entry.getId(), entry.getDurationMs());
    }

    public void fail(ScheduleRunLogEntity entry, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        entry.setStatus("failed");
        entry.setFinishedAt(now);
        entry.setDurationMs((int) ChronoUnit.MILLIS.between(entry.getStartedAt(), now));
        entry.setErrorMessage(errorMessage);
        repository.save(entry);
        log.debug("Schedule run log failed: id={}, error={}", entry.getId(), errorMessage);
    }

    public List<ScheduleRunLogEntity> listByTask(String taskId, int limit) {
        return repository.findByTaskIdOrderByStartedAtDesc(taskId, PageRequest.of(0, limit));
    }

    public List<ScheduleRunLogEntity> listRecent(int limit) {
        return repository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit));
    }
}
