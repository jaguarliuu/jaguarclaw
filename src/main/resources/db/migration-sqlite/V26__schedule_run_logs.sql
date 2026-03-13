CREATE TABLE IF NOT EXISTS schedule_run_logs (
    id              VARCHAR(36) PRIMARY KEY,
    task_id         VARCHAR(36) NOT NULL,
    task_name       VARCHAR(200) NOT NULL,
    triggered_by    VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    started_at      TIMESTAMP NOT NULL,
    finished_at     TIMESTAMP,
    duration_ms     INTEGER,
    error_message   TEXT,
    session_id      VARCHAR(36),
    run_id          VARCHAR(36),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_schedule_run_logs_task_id ON schedule_run_logs(task_id);
CREATE INDEX idx_schedule_run_logs_started_at ON schedule_run_logs(started_at DESC);
