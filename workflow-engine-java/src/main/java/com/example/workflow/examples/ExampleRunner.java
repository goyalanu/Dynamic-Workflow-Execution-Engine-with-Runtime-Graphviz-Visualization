package com.example.workflow.examples;

import com.example.workflow.execution.StepExecutionException;
import com.example.workflow.execution.WorkflowEngine;
import com.example.workflow.persistence.InMemoryWorkflowStateStore;
import com.example.workflow.visualization.DashboardRenderer;
import com.example.workflow.visualization.GraphvizRenderer;
import com.example.workflow.workflow.FailureKind;
import com.example.workflow.workflow.WorkflowConfigLoader;
import com.example.workflow.workflow.WorkflowDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

public final class ExampleRunner {
    private ExampleRunner() {
    }

    public static void main(String[] args) throws IOException {
        WorkflowDefinition workflow = WorkflowConfigLoader.loadFromClasspath("config/workflows.properties");
        Path outputDir = Path.of("generated");
        Files.createDirectories(outputDir);

        InMemoryWorkflowStateStore successStore = new InMemoryWorkflowStateStore();
        WorkflowEngine successEngine = new WorkflowEngine(workflow, successStore);
        String successRunId = successEngine.startRun("success-run");
        successEngine.executeUntilStable(successRunId);

        GraphvizRenderer renderer = new GraphvizRenderer();
        String successDot = renderer.render(workflow, successStore, successRunId);
        Files.writeString(outputDir.resolve("success.dot"), successDot);

        InMemoryWorkflowStateStore failureStore = new InMemoryWorkflowStateStore();
        WorkflowEngine failureEngine = new WorkflowEngine(
                workflow,
                failureStore,
                Map.of("ReserveInventory", (runId, stepName) -> {
                    throw new StepExecutionException("inventory unavailable", FailureKind.PERMANENT);
                }),
                4,
                Clock.systemUTC()
        );
        String failureRunId = failureEngine.startRun("failure-run");
        failureEngine.executeUntilStable(failureRunId);
        String failureDot = renderer.render(workflow, failureStore, failureRunId);
        Files.writeString(outputDir.resolve("failure.dot"), failureDot);
        Files.writeString(outputDir.resolve("dashboard.html"), new DashboardRenderer().render());

        System.out.println("wrote " + outputDir.resolve("success.dot").toAbsolutePath());
        System.out.println("wrote " + outputDir.resolve("failure.dot").toAbsolutePath());
        System.out.println("wrote " + outputDir.resolve("dashboard.html").toAbsolutePath());
    }
}
