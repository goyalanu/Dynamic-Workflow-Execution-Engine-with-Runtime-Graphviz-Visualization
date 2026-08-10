package com.example.workflow.service;

import com.example.workflow.execution.StepExecutionException;
import com.example.workflow.execution.StepHandler;
import com.example.workflow.execution.WorkflowEngine;
import com.example.workflow.persistence.InMemoryWorkflowStateStore;
import com.example.workflow.persistence.StepRun;
import com.example.workflow.persistence.WorkflowStateStore;
import com.example.workflow.visualization.GraphvizRenderer;
import com.example.workflow.workflow.FailureKind;
import com.example.workflow.workflow.StepStatus;
import com.example.workflow.workflow.WorkflowDefinition;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WorkflowRunService implements AutoCloseable {
    private final WorkflowDefinition workflow;
    private final WorkflowStateStore store;
    private final GraphvizRenderer renderer;
    private final ExecutorService executor;
    private final Set<String> knownRuns = ConcurrentHashMap.newKeySet();

    public WorkflowRunService(WorkflowDefinition workflow) {
        this(workflow, new InMemoryWorkflowStateStore(), new GraphvizRenderer());
    }

    public WorkflowRunService(WorkflowDefinition workflow, WorkflowStateStore store, GraphvizRenderer renderer) {
        this.workflow = workflow;
        this.store = store;
        this.renderer = renderer;
        this.executor = Executors.newCachedThreadPool();
    }

    public String startRun(RunMode mode) {
        WorkflowEngine engine = new WorkflowEngine(workflow, store, handlersFor(mode), 4, Clock.systemUTC());
        String runId = engine.startRun();
        knownRuns.add(runId);
        executor.submit(() -> engine.executeUntilStable(runId));
        return runId;
    }

    public List<String> runIds() {
        return new ArrayList<>(knownRuns);
    }

    public String stateJson(String runId) {
        List<StepRun> steps = store.listSteps(runId);
        Map<StepStatus, Integer> counts = new LinkedHashMap<>();
        for (StepStatus status : StepStatus.values()) {
            counts.put(status, 0);
        }
        for (StepRun step : steps) {
            counts.put(step.status(), counts.get(step.status()) + 1);
        }

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"runId\":\"").append(escapeJson(runId)).append("\",");
        json.append("\"workflowName\":\"").append(escapeJson(workflow.name())).append("\",");
        json.append("\"complete\":").append(isTerminal(steps)).append(",");
        json.append("\"counts\":{");
        int index = 0;
        for (Map.Entry<StepStatus, Integer> entry : counts.entrySet()) {
            if (index++ > 0) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
        }
        json.append("},");
        json.append("\"steps\":[");
        for (int i = 0; i < steps.size(); i++) {
            StepRun step = steps.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{")
                    .append("\"name\":\"").append(escapeJson(step.stepName())).append("\",")
                    .append("\"displayName\":\"").append(escapeJson(step.displayName())).append("\",")
                    .append("\"status\":\"").append(step.status()).append("\",")
                    .append("\"retryCount\":").append(step.retryCount()).append(",")
                    .append("\"maxRetries\":").append(step.maxRetries()).append(",")
                    .append("\"error\":").append(nullableJson(step.error()))
                    .append("}");
        }
        json.append("]}");
        return json.toString();
    }

    public String dot(String runId) {
        return renderer.render(workflow, store, runId);
    }

    private Map<String, StepHandler> handlersFor(RunMode mode) {
        Map<String, StepHandler> handlers = new LinkedHashMap<>();
        for (String stepName : workflow.steps().keySet()) {
            handlers.put(stepName, delayedHandler(550));
        }
        if (mode == RunMode.FAILURE) {
            handlers.put("ReserveInventory", (runId, stepName) -> {
                sleep(550);
                throw new StepExecutionException("inventory unavailable", FailureKind.PERMANENT);
            });
        }
        if (mode == RunMode.RETRY_THEN_SUCCESS) {
            Set<String> failedOnce = ConcurrentHashMap.newKeySet();
            handlers.put("ReserveInventory", (runId, stepName) -> {
                sleep(550);
                if (failedOnce.add(runId + ":" + stepName)) {
                    throw new StepExecutionException("temporary inventory lock", FailureKind.TRANSIENT);
                }
            });
        }
        return handlers;
    }

    private StepHandler delayedHandler(long millis) {
        return (runId, stepName) -> sleep(millis);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", exception);
        }
    }

    private boolean isTerminal(List<StepRun> steps) {
        return steps.stream().noneMatch(step -> step.status() == StepStatus.PENDING
                || step.status() == StepStatus.RETRYING
                || step.status() == StepStatus.RUNNING);
    }

    private String nullableJson(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
