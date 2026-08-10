package com.example.workflow.execution;

import com.example.workflow.persistence.StepRun;
import com.example.workflow.persistence.WorkflowStateStore;
import com.example.workflow.workflow.FailureKind;
import com.example.workflow.workflow.FailurePolicy;
import com.example.workflow.workflow.StepStatus;
import com.example.workflow.workflow.WorkflowDefinition;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class WorkflowEngine {
    private final WorkflowDefinition workflow;
    private final WorkflowStateStore store;
    private final Map<String, StepHandler> handlers;
    private final int maxWorkers;
    private final Clock clock;

    public WorkflowEngine(WorkflowDefinition workflow, WorkflowStateStore store) {
        this(workflow, store, Map.of(), 4, Clock.systemUTC());
    }

    public WorkflowEngine(
            WorkflowDefinition workflow,
            WorkflowStateStore store,
            Map<String, StepHandler> handlers,
            int maxWorkers,
            Clock clock
    ) {
        this.workflow = workflow;
        this.store = store;
        this.handlers = new HashMap<>(handlers);
        this.maxWorkers = maxWorkers;
        this.clock = clock;
    }

    public String startRun() {
        return startRun(null);
    }

    public String startRun(String requestedRunId) {
        return store.createRun(workflow, requestedRunId);
    }

    public void executeUntilStable(String runId) {
        while (true) {
            int progressed = executeReady(runId);
            boolean activeWork = store.statuses(runId).values().stream()
                    .anyMatch(status -> status == StepStatus.PENDING
                            || status == StepStatus.RETRYING
                            || status == StepStatus.RUNNING);
            if (progressed == 0 || !activeWork) {
                return;
            }
        }
    }

    public int executeReady(String runId) {
        List<String> ready = workflow.runnableSteps(store.statuses(runId));
        if (ready.isEmpty()) {
            return 0;
        }

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(maxWorkers, ready.size()));
        try {
            List<Future<Boolean>> results = ready.stream()
                    .map(stepName -> executor.submit(() -> executeStep(runId, stepName)))
                    .toList();
            int progressed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    progressed++;
                }
            }
            return progressed;
        } catch (Exception exception) {
            throw new IllegalStateException("Workflow execution failed", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    public boolean executeStep(String runId, String stepName) {
        workflow.step(stepName);
        if (!dependenciesCompleted(runId, stepName)) {
            return false;
        }
        if (!store.claimStep(runId, stepName)) {
            return false;
        }

        StepHandler handler = handlers.getOrDefault(stepName, (ignoredRunId, ignoredStepName) -> {
        });
        try {
            handler.execute(runId, stepName);
            store.completeStep(runId, stepName);
            return true;
        } catch (StepExecutionException exception) {
            handleFailure(runId, stepName, exception);
            return true;
        }
    }

    private boolean dependenciesCompleted(String runId, String stepName) {
        Map<String, StepStatus> statuses = store.statuses(runId);
        return workflow.step(stepName).dependencies().stream()
                .allMatch(dependency -> statuses.get(dependency) == StepStatus.COMPLETED);
    }

    private void handleFailure(String runId, String stepName, StepExecutionException exception) {
        StepRun step = store.getStep(runId, stepName);
        boolean canRetry = exception.failureKind() == FailureKind.TRANSIENT
                && step.retryCount() < step.maxRetries();
        if (canRetry) {
            int nextRetryCount = step.retryCount() + 1;
            Instant nextRetryAt = clock.instant().plus(workflow.retryPolicy().delayForAttempt(nextRetryCount));
            store.markRetrying(runId, stepName, nextRetryAt, exception.getMessage());
            return;
        }

        store.failStep(runId, stepName, exception.getMessage());
        Set<String> toBlock;
        if (workflow.failurePolicy() == FailurePolicy.BLOCK_REMAINING) {
            Set<String> ancestors = workflow.ancestorsOf(stepName);
            toBlock = new HashSet<>();
            for (Map.Entry<String, StepStatus> entry : store.statuses(runId).entrySet()) {
                if (!entry.getKey().equals(stepName) && !ancestors.contains(entry.getKey())) {
                    toBlock.add(entry.getKey());
                }
            }
        } else {
            toBlock = workflow.downstreamOf(stepName);
        }
        store.blockSteps(runId, toBlock, "blocked by failed step " + stepName);
    }
}
