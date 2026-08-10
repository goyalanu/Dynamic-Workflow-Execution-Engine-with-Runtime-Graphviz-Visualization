package com.example.workflow.execution;

@FunctionalInterface
public interface StepHandler {
    void execute(String runId, String stepName) throws StepExecutionException;
}
