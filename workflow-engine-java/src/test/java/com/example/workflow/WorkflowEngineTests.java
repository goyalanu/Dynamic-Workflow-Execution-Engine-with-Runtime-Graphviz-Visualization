package com.example.workflow;

import com.example.workflow.execution.StepExecutionException;
import com.example.workflow.execution.WorkflowEngine;
import com.example.workflow.persistence.InMemoryWorkflowStateStore;
import com.example.workflow.service.WorkflowRunService;
import com.example.workflow.visualization.GraphvizRenderer;
import com.example.workflow.workflow.FailureKind;
import com.example.workflow.workflow.RetryPolicy;
import com.example.workflow.workflow.StepDefinition;
import com.example.workflow.workflow.StepStatus;
import com.example.workflow.workflow.WorkflowConfigLoader;
import com.example.workflow.workflow.WorkflowDefinition;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorkflowEngineTests {
    public static void main(String[] args) {
        loadsConfig();
        rejectsCycles();
        successfulRunCompletesAllSteps();
        duplicateStepExecutionIsIdempotent();
        transientFailureRetriesThenCompletes();
        permanentFailureBlocksRemainingWork();
        dotIsDerivedFromPersistedState();
        serviceExposesRunStateJson();
        System.out.println("All Java workflow-engine tests passed.");
    }

    private static void loadsConfig() {
        WorkflowDefinition workflow = workflow();
        assertEquals("OrderFulfillment", workflow.name());
        assertEquals(2, workflow.retryPolicy().maxRetries());
        assertEquals(
                java.util.List.of("ReserveInventory", "GenerateInvoice"),
                workflow.step("SendNotification").dependencies()
        );
    }

    private static void rejectsCycles() {
        Map<String, StepDefinition> steps = new LinkedHashMap<>();
        steps.put("A", new StepDefinition("A", "A", java.util.List.of("B")));
        steps.put("B", new StepDefinition("B", "B", java.util.List.of("A")));
        assertThrows(() -> new WorkflowDefinition(
                "Cycle",
                "",
                steps,
                new RetryPolicy(0, java.time.Duration.ZERO, RetryPolicy.Backoff.FIXED),
                com.example.workflow.workflow.FailurePolicy.BLOCK_DEPENDENTS
        ));
    }

    private static void successfulRunCompletesAllSteps() {
        WorkflowDefinition workflow = workflow();
        InMemoryWorkflowStateStore store = new InMemoryWorkflowStateStore();
        WorkflowEngine engine = new WorkflowEngine(workflow, store);
        String runId = engine.startRun("success");

        engine.executeUntilStable(runId);

        assertEquals(StepStatus.COMPLETED, store.statuses(runId).get("ValidateOrder"));
        assertEquals(StepStatus.COMPLETED, store.statuses(runId).get("ProcessPayment"));
        assertEquals(StepStatus.COMPLETED, store.statuses(runId).get("ReserveInventory"));
        assertEquals(StepStatus.COMPLETED, store.statuses(runId).get("GenerateInvoice"));
        assertEquals(StepStatus.COMPLETED, store.statuses(runId).get("SendNotification"));
    }

    private static void duplicateStepExecutionIsIdempotent() {
        WorkflowDefinition workflow = workflow();
        InMemoryWorkflowStateStore store = new InMemoryWorkflowStateStore();
        AtomicInteger calls = new AtomicInteger();
        WorkflowEngine engine = new WorkflowEngine(
                workflow,
                store,
                Map.of("ValidateOrder", (runId, stepName) -> calls.incrementAndGet()),
                4,
                Clock.systemUTC()
        );
        String runId = engine.startRun("idempotent");

        assertTrue(engine.executeStep(runId, "ValidateOrder"));
        assertFalse(engine.executeStep(runId, "ValidateOrder"));
        assertEquals(1, calls.get());
        assertEquals(StepStatus.COMPLETED, store.getStep(runId, "ValidateOrder").status());
    }

    private static void transientFailureRetriesThenCompletes() {
        WorkflowDefinition workflow = workflow();
        InMemoryWorkflowStateStore store = new InMemoryWorkflowStateStore();
        Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
        WorkflowEngine engine = new WorkflowEngine(
                workflow,
                store,
                Map.of("ReserveInventory", (runId, stepName) -> {
                    int count = calls.computeIfAbsent(stepName, ignored -> new AtomicInteger()).incrementAndGet();
                    if (count == 1) {
                        throw new StepExecutionException("temporary lock", FailureKind.TRANSIENT);
                    }
                }),
                4,
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC)
        );
        String runId = engine.startRun("retry");

        engine.executeUntilStable(runId);

        assertEquals(StepStatus.COMPLETED, store.getStep(runId, "ReserveInventory").status());
        assertEquals(1, store.getStep(runId, "ReserveInventory").retryCount());
    }

    private static void permanentFailureBlocksRemainingWork() {
        WorkflowDefinition workflow = workflow();
        InMemoryWorkflowStateStore store = new InMemoryWorkflowStateStore();
        WorkflowEngine engine = failingInventoryEngine(workflow, store);
        String runId = engine.startRun("failure");

        engine.executeUntilStable(runId);

        assertEquals(StepStatus.COMPLETED, store.statuses(runId).get("ValidateOrder"));
        assertEquals(StepStatus.COMPLETED, store.statuses(runId).get("ProcessPayment"));
        assertEquals(StepStatus.FAILED, store.statuses(runId).get("ReserveInventory"));
        assertEquals(StepStatus.BLOCKED, store.statuses(runId).get("GenerateInvoice"));
        assertEquals(StepStatus.BLOCKED, store.statuses(runId).get("SendNotification"));
    }

    private static void dotIsDerivedFromPersistedState() {
        WorkflowDefinition workflow = workflow();
        InMemoryWorkflowStateStore store = new InMemoryWorkflowStateStore();
        WorkflowEngine engine = failingInventoryEngine(workflow, store);
        String runId = engine.startRun("dot");
        engine.executeUntilStable(runId);

        String dot = new GraphvizRenderer().render(workflow, store, runId);

        assertTrue(dot.contains("ReserveInventory [label=\"Reserve Inventory\\nFAILED\""));
        assertTrue(dot.contains("GenerateInvoice [label=\"Generate Invoice\\nBLOCKED\""));
        assertTrue(dot.contains("ProcessPayment -> ReserveInventory;"));
        assertTrue(dot.contains("GenerateInvoice -> SendNotification;"));
    }

    private static void serviceExposesRunStateJson() {
        WorkflowDefinition workflow = workflow();
        try (WorkflowRunService service = new WorkflowRunService(workflow)) {
            String runId = service.startRun(com.example.workflow.service.RunMode.SUCCESS);
            waitForCompletion(service, runId);
            String json = service.stateJson(runId);

            assertTrue(json.contains("\"workflowName\":\"OrderFulfillment\""));
            assertTrue(json.contains("\"complete\":true"));
            assertTrue(json.contains("\"status\":\"COMPLETED\""));
            assertTrue(service.dot(runId).contains("ValidateOrder -> ProcessPayment;"));
        }
    }

    private static void waitForCompletion(WorkflowRunService service, String runId) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (service.stateJson(runId).contains("\"complete\":true")) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for completion");
            }
        }
        throw new AssertionError("Run did not complete");
    }

    private static WorkflowEngine failingInventoryEngine(WorkflowDefinition workflow, InMemoryWorkflowStateStore store) {
        return new WorkflowEngine(
                workflow,
                store,
                Map.of("ReserveInventory", (runId, stepName) -> {
                    throw new StepExecutionException("sold out", FailureKind.PERMANENT);
                }),
                4,
                Clock.systemUTC()
        );
    }

    private static WorkflowDefinition workflow() {
        return WorkflowConfigLoader.loadFromClasspath("config/workflows.properties");
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError("Expected condition to be false");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("Expected <" + expected + "> but got <" + actual + ">");
        }
    }

    private static void assertThrows(Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("Expected exception");
    }
}
