package com.splatage.wild_economy.testing.scenario;

import java.util.Objects;

public record ScenarioExecutionResult(
        Outcome outcome,
        String detail
) {
    public ScenarioExecutionResult {
        outcome = Objects.requireNonNull(outcome, "outcome");
    }

    public static ScenarioExecutionResult succeeded() {
        return new ScenarioExecutionResult(Outcome.SUCCESS, null);
    }

    public static ScenarioExecutionResult rejected(final String detail) {
        return new ScenarioExecutionResult(Outcome.REJECTED, detail);
    }

    public static ScenarioExecutionResult failed(final String detail) {
        return new ScenarioExecutionResult(Outcome.ERROR, detail);
    }

    public boolean success() {
        return this.outcome == Outcome.SUCCESS;
    }

    public boolean rejected() {
        return this.outcome == Outcome.REJECTED;
    }

    public boolean error() {
        return this.outcome == Outcome.ERROR;
    }

    public String rejectionReason() {
        return this.rejected() ? this.detail : null;
    }

    public String failureReason() {
        return this.error() ? this.detail : null;
    }

    public enum Outcome {
        SUCCESS,
        REJECTED,
        ERROR
    }
}
