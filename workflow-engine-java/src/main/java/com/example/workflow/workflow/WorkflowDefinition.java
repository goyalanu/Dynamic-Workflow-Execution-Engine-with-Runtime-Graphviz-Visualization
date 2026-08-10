package com.example.workflow.workflow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkflowDefinition {
    private final String name;
    private final String description;
    private final LinkedHashMap<String, StepDefinition> steps;
    private final RetryPolicy retryPolicy;
    private final FailurePolicy failurePolicy;

    public WorkflowDefinition(
            String name,
            String description,
            Map<String, StepDefinition> steps,
            RetryPolicy retryPolicy,
            FailurePolicy failurePolicy
    ) {
        this.name = name;
        this.description = description;
        this.steps = new LinkedHashMap<>(steps);
        this.retryPolicy = retryPolicy;
        this.failurePolicy = failurePolicy;
        validate();
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Map<String, StepDefinition> steps() {
        return Map.copyOf(steps);
    }

    public List<StepDefinition> orderedSteps() {
        return List.copyOf(steps.values());
    }

    public StepDefinition step(String name) {
        StepDefinition step = steps.get(name);
        if (step == null) {
            throw new IllegalArgumentException("Unknown step: " + name);
        }
        return step;
    }

    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    public FailurePolicy failurePolicy() {
        return failurePolicy;
    }

    public List<String> runnableSteps(Map<String, StepStatus> statuses) {
        List<String> runnable = new ArrayList<>();
        for (StepDefinition step : steps.values()) {
            StepStatus status = statuses.get(step.name());
            if (status != StepStatus.PENDING && status != StepStatus.RETRYING) {
                continue;
            }
            boolean dependenciesComplete = step.dependencies().stream()
                    .allMatch(dependency -> statuses.get(dependency) == StepStatus.COMPLETED);
            if (dependenciesComplete) {
                runnable.add(step.name());
            }
        }
        return runnable;
    }

    public Set<String> downstreamOf(String failedStep) {
        Set<String> downstream = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (StepDefinition step : steps.values()) {
                if (downstream.contains(step.name())) {
                    continue;
                }
                boolean direct = step.dependencies().contains(failedStep);
                boolean indirect = step.dependencies().stream().anyMatch(downstream::contains);
                if (direct || indirect) {
                    downstream.add(step.name());
                    changed = true;
                }
            }
        }
        return downstream;
    }

    public Set<String> ancestorsOf(String stepName) {
        Set<String> ancestors = new HashSet<>();
        addAncestors(stepName, ancestors);
        return ancestors;
    }

    private void addAncestors(String stepName, Set<String> ancestors) {
        for (String dependency : step(stepName).dependencies()) {
            if (ancestors.add(dependency)) {
                addAncestors(dependency, ancestors);
            }
        }
    }

    private void validate() {
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Workflow must have at least one step");
        }
        for (StepDefinition step : steps.values()) {
            for (String dependency : step.dependencies()) {
                if (!steps.containsKey(dependency)) {
                    throw new IllegalArgumentException(step.name() + " depends on unknown step " + dependency);
                }
            }
        }
        for (String stepName : steps.keySet()) {
            assertAcyclic(stepName, new HashSet<>(), new HashSet<>());
        }
    }

    private void assertAcyclic(String stepName, Set<String> visiting, Set<String> visited) {
        if (visited.contains(stepName)) {
            return;
        }
        if (!visiting.add(stepName)) {
            throw new IllegalArgumentException("Cycle detected at " + stepName);
        }
        for (String dependency : step(stepName).dependencies()) {
            assertAcyclic(dependency, visiting, visited);
        }
        visiting.remove(stepName);
        visited.add(stepName);
    }
}
