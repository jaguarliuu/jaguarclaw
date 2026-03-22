ALTER TABLE schedule_run_logs
    ADD COLUMN IF NOT EXISTS trace_json TEXT;
