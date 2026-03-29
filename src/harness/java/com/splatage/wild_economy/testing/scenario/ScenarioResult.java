package com.splatage.wild_economy.testing.scenario;

import java.util.Map;
import java.util.Objects;

public record ScenarioResult(
        String scenarioName,
        long operations,
        long successes,
        long expectedRejections,
        long failures,
        long totalDurationNanos,
        long maxDurationNanos,
        String sampleRejection,
        String sampleFailure,
        Map<String, Long> rejectionReasons,
        Map<String, Long> failureReasons
) {
    public ScenarioResult {
        Objects.requireNonNull(scenarioName, "scenarioName");
        rejectionReasons = Map.copyOf(Objects.requireNonNull(rejectionReasons, "rejectionReasons"));
        failureReasons = Map.copyOf(Objects.requireNonNull(failureReasons, "failureReasons"));
    }

    public long averageMicros() {
        return this.operations <= 0L ? 0L : (this.totalDurationNanos / this.operations) / 1_000L;
    }

    public long maxMicros() {
        return this.maxDurationNanos / 1_000L;
    }

    public long successRatePercent() {
        return this.operations <= 0L ? 0L : Math.round((this.successes * 100.0d) / this.operations);
    }

    public String describe() {
        final StringBuilder builder = new StringBuilder(this.scenarioName)
                .append(": operations=")
                .append(this.operations)
                .append(", successes=")
                .append(this.successes)
                .append(", expectedRejections=")
                .append(this.expectedRejections)
                .append(", failures=")
                .append(this.failures)
                .append(", successRatePct=")
                .append(this.successRatePercent())
                .append(", avgMicros=")
                .append(this.averageMicros())
                .append(", maxMicros=")
                .append(this.maxMicros());
        if (this.sampleRejection != null && !this.sampleRejection.isBlank()) {
            builder.append(", sampleRejection=").append(this.sampleRejection);
        }
        if (this.sampleFailure != null && !this.sampleFailure.isBlank()) {
            builder.append(", sampleFailure=").append(this.sampleFailure);
        }
        return builder.toString();
    }
}
