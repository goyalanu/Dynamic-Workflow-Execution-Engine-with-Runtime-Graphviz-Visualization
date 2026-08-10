package com.example.workflow.workflow;

import java.util.List;

public record StepDefinition(String name, String displayName, List<String> dependencies) {
    public StepDefinition {
        dependencies = List.copyOf(dependencies);
    }
}
