CREATE TABLE IF NOT EXISTS workflow_runs (
  run_id UUID PRIMARY KEY,
  workflow_name TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'BLOCKED', 'RETRYING')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS workflow_step_runs (
  run_id UUID NOT NULL REFERENCES workflow_runs(run_id) ON DELETE CASCADE,
  workflow_name TEXT NOT NULL,
  step_name TEXT NOT NULL,
  display_name TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'BLOCKED', 'RETRYING')),
  retry_count INTEGER NOT NULL DEFAULT 0,
  max_retries INTEGER NOT NULL DEFAULT 0,
  next_retry_at TIMESTAMPTZ,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  error TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (run_id, step_name)
);

CREATE INDEX IF NOT EXISTS idx_workflow_step_runs_status
  ON workflow_step_runs (run_id, status);
