package com.example.workflow.persistence;

import com.example.workflow.workflow.StepStatus;

import java.time.Instant;

public record WorkflowRun(
        String runId,
        String workflowName,
        StepStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
