package com.example.workflow.persistence;

import com.example.workflow.workflow.StepStatus;
import com.example.workflow.workflow.WorkflowDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface WorkflowStateStore {
    String createRun(WorkflowDefinition workflow, String requestedRunId);

    List<StepRun> listSteps(String runId);

    StepRun getStep(String runId, String stepName);

    Map<String, StepStatus> statuses(String runId);

    boolean claimStep(String runId, String stepName);

    void completeStep(String runId, String stepName);

    void markRetrying(String runId, String stepName, Instant nextRetryAt, String error);

    void failStep(String runId, String stepName, String error);

    void blockSteps(String runId, Set<String> stepNames, String reason);
}
