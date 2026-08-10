package com.example.workflow.visualization;

import com.example.workflow.persistence.StepRun;
import com.example.workflow.persistence.WorkflowStateStore;
import com.example.workflow.workflow.StepDefinition;
import com.example.workflow.workflow.StepStatus;
import com.example.workflow.workflow.WorkflowDefinition;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class GraphvizRenderer {
    private static final Map<StepStatus, String> COLORS = new EnumMap<>(StepStatus.class);

    static {
        COLORS.put(StepStatus.PENDING, "#d9e2ec");
        COLORS.put(StepStatus.RUNNING, "#7cc4ff");
        COLORS.put(StepStatus.COMPLETED, "#8fd19e");
        COLORS.put(StepStatus.FAILED, "#e27b7b");
        COLORS.put(StepStatus.BLOCKED, "#f5d26b");
        COLORS.put(StepStatus.RETRYING, "#c6a5ff");
    }

    public String render(WorkflowDefinition workflow, WorkflowStateStore store, String runId) {
        Map<String, StepRun> records = store.listSteps(runId).stream()
                .collect(Collectors.toMap(StepRun::stepName, step -> step));
        StringBuilder dot = new StringBuilder();
        dot.append("digraph WorkflowState {\n");
        dot.append("  rankdir=LR;\n");
        dot.append("  node [shape=ellipse, fontname=\"Arial\", style=filled, fontsize=12];\n\n");

        for (StepDefinition step : workflow.orderedSteps()) {
            StepRun record = records.get(step.name());
            if (record == null) {
                throw new IllegalStateException("Missing persisted state for " + step.name());
            }
            dot.append("  ")
                    .append(step.name())
                    .append(" [label=\"")
                    .append(escape(step.displayName()))
                    .append("\\n")
                    .append(record.status())
                    .append("\", fillcolor=\"")
                    .append(COLORS.get(record.status()))
                    .append("\"];\n");
        }

        dot.append("\n");
        for (StepDefinition step : workflow.orderedSteps()) {
            for (String dependency : step.dependencies()) {
                dot.append("  ")
                        .append(dependency)
                        .append(" -> ")
                        .append(step.name())
                        .append(";\n");
            }
        }
        dot.append("}\n");
        return dot.toString();
    }

    private String escape(String value) {
        return value.replace("\"", "\\\"");
    }
}
