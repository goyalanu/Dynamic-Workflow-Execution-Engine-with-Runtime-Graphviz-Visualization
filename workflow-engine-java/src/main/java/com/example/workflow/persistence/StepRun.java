package com.example.workflow.persistence;

import com.example.workflow.workflow.StepStatus;

import java.time.Instant;

public record StepRun(
        String runId,
        String workflowName,
        String stepName,
        String displayName,
        StepStatus status,
        int retryCount,
        int maxRetries,
        Instant nextRetryAt,
        Instant startedAt,
        Instant completedAt,
        String error
) {
}
