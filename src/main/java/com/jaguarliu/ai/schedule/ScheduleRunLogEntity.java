package com.jaguarliu.ai.schedule;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "schedule_run_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleRunLogEntity {

    @Id
    private String id;

    @Column(name = "task_id", nullable = false, length = 36)
    private String taskId;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    /** 'scheduled' | 'manual' */
    @Column(name = "triggered_by", nullable = false, length = 20)
    private String triggeredBy;

    /** 'running' | 'success' | 'failed' */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "run_id", length = 36)
    private String runId;

    @Column(name = "trace_json", columnDefinition = "TEXT")
    private String traceJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
    }
}
