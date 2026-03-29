package com.splatage.wild_economy.testing.scenario;

import java.util.List;
import java.util.Objects;

public record ScenarioRunReport(
        int requestedOperations,
        int concurrency,
        long configuredDurationSeconds,
        long completedOperations,
        long wallClockDurationNanos,
        StopReason stopReason,
        List<ScenarioResult> scenarioResults
) {
    public ScenarioRunReport {
        if (requestedOperations <= 0) {
            throw new IllegalArgumentException("requestedOperations must be positive");
        }
        if (concurrency <= 0) {
            throw new IllegalArgumentException("concurrency must be positive");
        }
        if (configuredDurationSeconds < 0L) {
            throw new IllegalArgumentException("configuredDurationSeconds cannot be negative");
        }
        if (completedOperations < 0L) {
            throw new IllegalArgumentException("completedOperations cannot be negative");
        }
        if (wallClockDurationNanos < 0L) {
            throw new IllegalArgumentException("wallClockDurationNanos cannot be negative");
        }
        stopReason = Objects.requireNonNull(stopReason, "stopReason");
        scenarioResults = List.copyOf(Objects.requireNonNull(scenarioResults, "scenarioResults"));
    }

    public long wallClockDurationMillis() {
        return this.wallClockDurationNanos / 1_000_000L;
    }

    public long operationsPerSecond() {
        if (this.wallClockDurationNanos <= 0L || this.completedOperations <= 0L) {
            return 0L;
        }
        return Math.round(this.completedOperations / (this.wallClockDurationNanos / 1_000_000_000.0d));
    }

    public enum StopReason {
        OPERATIONS,
        DURATION
    }
}
