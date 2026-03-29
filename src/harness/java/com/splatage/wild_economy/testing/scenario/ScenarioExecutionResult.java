package com.splatage.wild_economy.testing.scenario;

public record ScenarioExecutionResult(
        boolean success,
        String failureReason
) {
    public static ScenarioExecutionResult succeeded() {
        return new ScenarioExecutionResult(true, null);
    }

    public static ScenarioExecutionResult failed(final String failureReason) {
        return new ScenarioExecutionResult(false, failureReason);
    }
}
