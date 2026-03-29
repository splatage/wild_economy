package com.splatage.wild_economy.testing.scenario;

public record ScenarioResult(
        String scenarioName,
        long operations,
        long successes,
        long failures,
        long totalDurationNanos,
        long maxDurationNanos,
        String sampleFailure
) {
    public String describe() {
        final long averageMicros = this.operations <= 0L ? 0L : (this.totalDurationNanos / this.operations) / 1_000L;
        final long maxMicros = this.maxDurationNanos / 1_000L;
        final StringBuilder builder = new StringBuilder(this.scenarioName)
                .append(": operations=")
                .append(this.operations)
                .append(", successes=")
                .append(this.successes)
                .append(", failures=")
                .append(this.failures)
                .append(", avgMicros=")
                .append(averageMicros)
                .append(", maxMicros=")
                .append(maxMicros);
        if (this.sampleFailure != null && !this.sampleFailure.isBlank()) {
            builder.append(", sampleFailure=").append(this.sampleFailure);
        }
        return builder.toString();
    }
}
