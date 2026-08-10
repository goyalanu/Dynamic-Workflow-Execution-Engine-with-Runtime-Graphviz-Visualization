package com.example.workflow.workflow;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public final class WorkflowConfigLoader {
    private WorkflowConfigLoader() {
    }

    public static WorkflowDefinition loadFromClasspath(String resourceName) {
        try (InputStream input = WorkflowConfigLoader.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing classpath resource: " + resourceName);
            }
            Properties properties = new Properties();
            properties.load(input);
            return fromProperties(properties);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load workflow config", exception);
        }
    }

    public static WorkflowDefinition fromProperties(Properties properties) {
        String workflowName = required(properties, "workflow.name");
        String description = properties.getProperty("workflow.description", "");
        FailurePolicy failurePolicy = FailurePolicy.valueOf(
                properties.getProperty("workflow.failurePolicy", "BLOCK_DEPENDENTS")
        );
        RetryPolicy retryPolicy = new RetryPolicy(
                Integer.parseInt(properties.getProperty("workflow.retry.maxRetries", "0")),
                Duration.ofSeconds(Long.parseLong(properties.getProperty("workflow.retry.retryIntervalSeconds", "0"))),
                RetryPolicy.Backoff.valueOf(properties.getProperty("workflow.retry.backoff", "FIXED"))
        );

        Map<String, StepDefinition> steps = new LinkedHashMap<>();
        for (String stepName : splitCsv(required(properties, "workflow.steps"))) {
            String prefix = "step." + stepName + ".";
            String displayName = properties.getProperty(prefix + "displayName", stepName);
            List<String> dependencies = splitCsv(properties.getProperty(prefix + "dependencies", ""));
            steps.put(stepName, new StepDefinition(stepName, displayName, dependencies));
        }

        return new WorkflowDefinition(workflowName, description, steps, retryPolicy, failurePolicy);
    }

    private static String required(Properties properties, String key) {
        return Objects.requireNonNull(properties.getProperty(key), "Missing property: " + key);
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }
}
