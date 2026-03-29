package com.splatage.wild_economy.testing.scenario;

import com.splatage.wild_economy.bootstrap.HarnessBootstrap;
import com.splatage.wild_economy.testing.seed.SeedPlan;
import com.splatage.wild_economy.testing.scenario.impl.BrowseHeavyScenario;
import com.splatage.wild_economy.testing.scenario.impl.BuyHeavyScenario;
import com.splatage.wild_economy.testing.scenario.impl.MixedEconomyScenario;
import com.splatage.wild_economy.testing.scenario.impl.SellHeavyScenario;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class ScenarioRunnerImpl implements ScenarioRunner {

    private final List<Scenario> scenarios;

    public ScenarioRunnerImpl() {
        this.scenarios = List.of(
                new BrowseHeavyScenario(),
                new BuyHeavyScenario(),
                new SellHeavyScenario(),
                new MixedEconomyScenario()
        );
    }

    @Override
    public List<ScenarioResult> run(
            final HarnessBootstrap.HarnessComponents components,
            final SeedPlan seedPlan,
            final int operations,
            final int concurrency,
            final ScenarioMix mix
    ) {
        if (operations <= 0) {
            throw new IllegalArgumentException("Scenario operations must be positive");
        }
        if (concurrency <= 0) {
            throw new IllegalArgumentException("Scenario concurrency must be positive");
        }

        final ScenarioContext context = ScenarioContext.create(components, seedPlan);
        final Map<String, ScenarioAccumulator> accumulators = new ConcurrentHashMap<>();
        for (final Scenario scenario : this.scenarios) {
            accumulators.put(scenario.name(), new ScenarioAccumulator());
        }

        final AtomicLong threadCounter = new AtomicLong(1L);
        final ExecutorService executor = Executors.newFixedThreadPool(concurrency, runnable -> {
            final Thread thread = new Thread(runnable, "wild-economy-harness-scenario-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
        final AtomicLong operationCounter = new AtomicLong(0L);
        final List<Future<?>> futures = new ArrayList<>(concurrency);

        for (int worker = 0; worker < concurrency; worker++) {
            final long workerSeed = seedPlan.randomSeed() + (31L * (worker + 1));
            futures.add(executor.submit(() -> {
                final Random random = new Random(workerSeed);
                while (true) {
                    final long operationIndex = operationCounter.getAndIncrement();
                    if (operationIndex >= operations) {
                        return;
                    }
                    final int scenarioIndex = mix.selectIndex(random);
                    final Scenario scenario = this.scenarios.get(scenarioIndex);
                    final ScenarioSelection selection = switch (scenarioIndex) {
                        case 0 -> context.nextBrowseSelection(random);
                        case 1 -> context.nextBuySelection(random);
                        case 2 -> context.nextSellSelection(random);
                        default -> context.nextMixedSelection(random);
                    };
                    final long startedAt = System.nanoTime();
                    ScenarioExecutionResult executionResult;
                    try {
                        executionResult = scenario.execute(context, selection);
                    } catch (final RuntimeException exception) {
                        executionResult = ScenarioExecutionResult.failed(exception.getClass().getSimpleName() + ": " + exception.getMessage());
                    }
                    final long durationNanos = System.nanoTime() - startedAt;
                    accumulators.get(scenario.name()).record(executionResult, durationNanos);
                }
            }));
        }

        executor.shutdown();
        try {
            for (final Future<?> future : futures) {
                future.get();
            }
            if (!executor.awaitTermination(30L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (final Exception exception) {
            executor.shutdownNow();
            throw new IllegalStateException("Scenario runner failed", exception);
        }

        components.stockService().flushDirtyNow();
        components.storeRuntimeStateService().flushDirtyNow();

        return accumulators.entrySet().stream()
                .map(entry -> entry.getValue().toResult(entry.getKey()))
                .sorted(Comparator.comparing(ScenarioResult::scenarioName))
                .toList();
    }

    private static final class ScenarioAccumulator {
        private final LongAdder operations = new LongAdder();
        private final LongAdder successes = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder totalDurationNanos = new LongAdder();
        private final AtomicLong maxDurationNanos = new AtomicLong(0L);
        private volatile String sampleFailure;

        private void record(final ScenarioExecutionResult executionResult, final long durationNanos) {
            this.operations.increment();
            this.totalDurationNanos.add(durationNanos);
            this.maxDurationNanos.accumulateAndGet(durationNanos, Math::max);
            if (executionResult.success()) {
                this.successes.increment();
            } else {
                this.failures.increment();
                if (this.sampleFailure == null && executionResult.failureReason() != null) {
                    this.sampleFailure = executionResult.failureReason();
                }
            }
        }

        private ScenarioResult toResult(final String scenarioName) {
            return new ScenarioResult(
                    scenarioName,
                    this.operations.sum(),
                    this.successes.sum(),
                    this.failures.sum(),
                    this.totalDurationNanos.sum(),
                    this.maxDurationNanos.get(),
                    this.sampleFailure
            );
        }
    }
}
