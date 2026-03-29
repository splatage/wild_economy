package com.splatage.wild_economy.testing.scenario;

public interface Scenario {

    String name();

    ScenarioExecutionResult execute(ScenarioContext context, ScenarioSelection selection);
}
