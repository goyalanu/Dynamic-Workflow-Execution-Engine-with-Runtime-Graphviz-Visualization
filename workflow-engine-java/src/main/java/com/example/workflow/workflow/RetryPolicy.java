package com.example.workflow.workflow;

import java.time.Duration;

public record RetryPolicy(int maxRetries, Duration retryInterval, Backoff backoff) {
    public enum Backoff {
        FIXED,
        EXPONENTIAL
    }

    public Duration delayForAttempt(int retryCount) {
        if (backoff == Backoff.EXPONENTIAL) {
            long multiplier = 1L << Math.max(retryCount - 1, 0);
            return retryInterval.multipliedBy(multiplier);
        }
        return retryInterval;
    }
}
