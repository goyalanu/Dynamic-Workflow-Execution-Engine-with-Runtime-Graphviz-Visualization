package com.example.workflow.persistence;

import com.example.workflow.workflow.StepDefinition;
import com.example.workflow.workflow.StepStatus;
import com.example.workflow.workflow.WorkflowDefinition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class InMemoryWorkflowStateStore implements WorkflowStateStore {
    private final Map<String, WorkflowRun> runs = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<String, MutableStepRun>> stepRuns = new LinkedHashMap<>();

    @Override
    public synchronized String createRun(WorkflowDefinition workflow, String requestedRunId) {
        String runId = requestedRunId == null || requestedRunId.isBlank()
                ? UUID.randomUUID().toString()
                : requestedRunId;
        Instant now = Instant.now();
        runs.putIfAbsent(runId, new WorkflowRun(runId, workflow.name(), StepStatus.RUNNING, now, now));
        stepRuns.computeIfAbsent(runId, ignored -> {
            LinkedHashMap<String, MutableStepRun> records = new LinkedHashMap<>();
            for (StepDefinition step : workflow.orderedSteps()) {
                records.put(step.name(), new MutableStepRun(
                        runId,
                        workflow.name(),
                        step.name(),
                        step.displayName(),
                        StepStatus.PENDING,
                        0,
                        workflow.retryPolicy().maxRetries()
                ));
            }
            return records;
        });
        return runId;
    }

    @Override
    public synchronized List<StepRun> listSteps(String runId) {
        return new ArrayList<>(stepsFor(runId).values().stream().map(MutableStepRun::snapshot).toList());
    }

    @Override
    public synchronized StepRun getStep(String runId, String stepName) {
        MutableStepRun step = stepsFor(runId).get(stepName);
        if (step == null) {
            throw new IllegalArgumentException("Unknown step " + stepName + " for run " + runId);
        }
        return step.snapshot();
    }

    @Override
    public synchronized Map<String, StepStatus> statuses(String runId) {
        LinkedHashMap<String, StepStatus> statuses = new LinkedHashMap<>();
        for (MutableStepRun step : stepsFor(runId).values()) {
            statuses.put(step.stepName, step.status);
        }
        return statuses;
    }

    @Override
    public synchronized boolean claimStep(String runId, String stepName) {
        MutableStepRun step = mutableStep(runId, stepName);
        if (step.status != StepStatus.PENDING && step.status != StepStatus.RETRYING) {
            return false;
        }
        step.status = StepStatus.RUNNING;
        if (step.startedAt == null) {
            step.startedAt = Instant.now();
        }
        step.nextRetryAt = null;
        return true;
    }

    @Override
    public synchronized void completeStep(String runId, String stepName) {
        MutableStepRun step = mutableStep(runId, stepName);
        if (step.status == StepStatus.RUNNING) {
            step.status = StepStatus.COMPLETED;
            step.completedAt = Instant.now();
            step.error = null;
        }
    }

    @Override
    public synchronized void markRetrying(String runId, String stepName, Instant nextRetryAt, String error) {
        MutableStepRun step = mutableStep(runId, stepName);
        step.status = StepStatus.RETRYING;
        step.retryCount++;
        step.nextRetryAt = nextRetryAt;
        step.error = error;
    }

    @Override
    public synchronized void failStep(String runId, String stepName, String error) {
        MutableStepRun step = mutableStep(runId, stepName);
        step.status = StepStatus.FAILED;
        step.completedAt = Instant.now();
        step.error = error;
    }

    @Override
    public synchronized void blockSteps(String runId, Set<String> stepNames, String reason) {
        for (String stepName : stepNames) {
            MutableStepRun step = mutableStep(runId, stepName);
            if (step.status == StepStatus.PENDING
                    || step.status == StepStatus.RETRYING
                    || step.status == StepStatus.RUNNING
                    || step.status == StepStatus.COMPLETED) {
                step.status = StepStatus.BLOCKED;
                step.completedAt = Instant.now();
                step.error = reason;
            }
        }
    }

    private LinkedHashMap<String, MutableStepRun> stepsFor(String runId) {
        LinkedHashMap<String, MutableStepRun> steps = stepRuns.get(runId);
        if (steps == null) {
            throw new IllegalArgumentException("Unknown run: " + runId);
        }
        return steps;
    }

    private MutableStepRun mutableStep(String runId, String stepName) {
        MutableStepRun step = stepsFor(runId).get(stepName);
        if (step == null) {
            throw new IllegalArgumentException("Unknown step " + stepName + " for run " + runId);
        }
        return step;
    }

    private static final class MutableStepRun {
        private final String runId;
        private final String workflowName;
        private final String stepName;
        private final String displayName;
        private StepStatus status;
        private int retryCount;
        private final int maxRetries;
        private Instant nextRetryAt;
        private Instant startedAt;
        private Instant completedAt;
        private String error;

        private MutableStepRun(
                String runId,
                String workflowName,
                String stepName,
                String displayName,
                StepStatus status,
                int retryCount,
                int maxRetries
        ) {
            this.runId = runId;
            this.workflowName = workflowName;
            this.stepName = stepName;
            this.displayName = displayName;
            this.status = status;
            this.retryCount = retryCount;
            this.maxRetries = maxRetries;
        }

        private StepRun snapshot() {
            return new StepRun(
                    runId,
                    workflowName,
                    stepName,
                    displayName,
                    status,
                    retryCount,
                    maxRetries,
                    nextRetryAt,
                    startedAt,
                    completedAt,
                    error
            );
        }
    }
}
