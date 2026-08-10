package com.example.workflow.execution;

import com.example.workflow.workflow.FailureKind;

public final class StepExecutionException extends Exception {
    private final FailureKind failureKind;

    public StepExecutionException(String message, FailureKind failureKind) {
        super(message);
        this.failureKind = failureKind;
    }

    public FailureKind failureKind() {
        return failureKind;
    }
}
