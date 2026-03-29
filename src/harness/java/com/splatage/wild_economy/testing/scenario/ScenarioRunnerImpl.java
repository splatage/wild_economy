package com.splatage.wild_economy.testing.scenario;

import com.splatage.wild_economy.bootstrap.HarnessBootstrap;
import com.splatage.wild_economy.testing.seed.SeedPlan;
import com.splatage.wild_economy.testing.scenario.impl.BrowseHeavyScenario;
import com.splatage.wild_economy.testing.scenario.impl.BuyHeavyScenario;
import com.splatage.wild_economy.testing.scenario.impl.MixedEconomyScenario;
import com.splatage.wild_economy.testing.scenario.impl.SellHeavyScenario;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    public ScenarioRunReport run(
            final HarnessBootstrap.HarnessComponents components,
            final SeedPlan seedPlan,
            final int operations,
            final int concurrency,
            final long durationSeconds,
            final ScenarioMix mix
    ) {
        if (operations <= 0) {
            throw new IllegalArgumentException("Scenario operations must be positive");
        }
        if (concurrency <= 0) {
            throw new IllegalArgumentException("Scenario concurrency must be positive");
        }
        if (durationSeconds < 0L) {
            throw new IllegalArgumentException("Scenario durationSeconds cannot be negative");
        }

        final ScenarioContext context = ScenarioContext.create(components, seedPlan);
        final Map<String, ScenarioAccumulator> accumulators = new ConcurrentHashMap<>();
        for (final Scenario scenario : this.scenarios) {
            accumulators.put(scenario.name(), new ScenarioAccumulator());
        }

        final long startedAt = System.nanoTime();
        final long durationNanos = durationSeconds <= 0L ? Long.MAX_VALUE : TimeUnit.SECONDS.toNanos(durationSeconds);
        final long deadlineNanos = durationSeconds <= 0L ? Long.MAX_VALUE : safeAdd(startedAt, durationNanos);

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
                    if (System.nanoTime() >= deadlineNanos) {
                        return;
                    }
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
                    final long opStartedAt = System.nanoTime();
                    ScenarioExecutionResult executionResult;
                    try {
                        executionResult = scenario.execute(context, selection);
                    } catch (final RuntimeException exception) {
                        executionResult = ScenarioExecutionResult.failed(exception.getClass().getSimpleName() + ": " + exception.getMessage());
                    }
                    final long opDurationNanos = System.nanoTime() - opStartedAt;
                    accumulators.get(scenario.name()).record(executionResult, opDurationNanos);
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

        final long finishedAt = System.nanoTime();
        components.stockService().flushDirtyNow();
        components.storeRuntimeStateService().flushDirtyNow();

        final List<ScenarioResult> scenarioResults = accumulators.entrySet().stream()
                .map(entry -> entry.getValue().toResult(entry.getKey()))
                .sorted(Comparator.comparing(ScenarioResult::scenarioName))
                .toList();
        final long completedOperations = scenarioResults.stream().mapToLong(ScenarioResult::operations).sum();
        final ScenarioRunReport.StopReason stopReason = completedOperations >= operations
                ? ScenarioRunReport.StopReason.OPERATIONS
                : ScenarioRunReport.StopReason.DURATION;

        return new ScenarioRunReport(
                operations,
                concurrency,
                durationSeconds,
                completedOperations,
                finishedAt - startedAt,
                stopReason,
                scenarioResults
        );
    }

    private static long safeAdd(final long left, final long right) {
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static final class ScenarioAccumulator {
        private final LongAdder operations = new LongAdder();
        private final LongAdder successes = new LongAdder();
        private final LongAdder expectedRejections = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder totalDurationNanos = new LongAdder();
        private final AtomicLong maxDurationNanos = new AtomicLong(0L);
        private final ConcurrentHashMap<String, LongAdder> rejectionReasons = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, LongAdder> failureReasons = new ConcurrentHashMap<>();
        private volatile String sampleRejection;
        private volatile String sampleFailure;

        private void record(final ScenarioExecutionResult executionResult, final long durationNanos) {
            this.operations.increment();
            this.totalDurationNanos.add(durationNanos);
            this.maxDurationNanos.accumulateAndGet(durationNanos, Math::max);
            if (executionResult.success()) {
                this.successes.increment();
                return;
            }
            if (executionResult.rejected()) {
                this.expectedRejections.increment();
                this.incrementReason(this.rejectionReasons, executionResult.rejectionReason());
                if (this.sampleRejection == null && executionResult.rejectionReason() != null) {
                    this.sampleRejection = executionResult.rejectionReason();
                }
                return;
            }
            this.failures.increment();
            this.incrementReason(this.failureReasons, executionResult.failureReason());
            if (this.sampleFailure == null && executionResult.failureReason() != null) {
                this.sampleFailure = executionResult.failureReason();
            }
        }

        private void incrementReason(final ConcurrentHashMap<String, LongAdder> reasons, final String reason) {
            final String key = reason == null || reason.isBlank() ? "(unspecified)" : reason;
            reasons.computeIfAbsent(key, ignored -> new LongAdder()).increment();
        }

        private ScenarioResult toResult(final String scenarioName) {
            return new ScenarioResult(
                    scenarioName,
                    this.operations.sum(),
                    this.successes.sum(),
                    this.expectedRejections.sum(),
                    this.failures.sum(),
                    this.totalDurationNanos.sum(),
                    this.maxDurationNanos.get(),
                    this.sampleRejection,
                    this.sampleFailure,
                    toCountMap(this.rejectionReasons),
                    toCountMap(this.failureReasons)
            );
        }

        private static Map<String, Long> toCountMap(final ConcurrentHashMap<String, LongAdder> reasons) {
            final List<Map.Entry<String, LongAdder>> entries = new ArrayList<>(reasons.entrySet());
            entries.sort((left, right) -> {
                final int countComparison = Long.compare(right.getValue().sum(), left.getValue().sum());
                if (countComparison != 0) {
                    return countComparison;
                }
                return left.getKey().compareToIgnoreCase(right.getKey());
            });
            final Map<String, Long> counts = new LinkedHashMap<>();
            for (final Map.Entry<String, LongAdder> entry : entries) {
                counts.put(entry.getKey(), entry.getValue().sum());
            }
            return counts;
        }
    }
}
