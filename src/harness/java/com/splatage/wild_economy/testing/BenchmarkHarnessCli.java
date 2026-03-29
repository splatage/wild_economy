package com.splatage.wild_economy.testing;

import com.splatage.wild_economy.bootstrap.HarnessBootstrap;
import com.splatage.wild_economy.testing.scenario.ScenarioResult;
import com.splatage.wild_economy.testing.scenario.ScenarioRunReport;
import com.splatage.wild_economy.testing.scenario.ScenarioRunner;
import com.splatage.wild_economy.testing.scenario.ScenarioRunnerImpl;
import com.splatage.wild_economy.testing.seed.SeedGenerator;
import com.splatage.wild_economy.testing.seed.SeedGeneratorImpl;
import com.splatage.wild_economy.testing.seed.SeedPlan;
import com.splatage.wild_economy.testing.seed.SeedRunReport;
import com.splatage.wild_economy.testing.verify.DatasetVerifier;
import com.splatage.wild_economy.testing.verify.DatasetVerifierImpl;
import com.splatage.wild_economy.testing.verify.InvariantReport;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class BenchmarkHarnessCli {

    private BenchmarkHarnessCli() {
    }

    public static void main(final String[] args) {
        final Map<String, String> parsed = parseArgs(args);
        final File configDirectory = new File(parsed.getOrDefault("config-dir", "src/main/resources"));
        final File harnessConfigFile = new File(parsed.getOrDefault("harness-config", "src/harness/resources/harness.yml"));
        final File reportFile = parsed.containsKey("report-file") ? new File(parsed.get("report-file")) : null;
        final HarnessConfig harnessConfig = HarnessConfig.load(harnessConfigFile);
        final TestProfile profile = parsed.containsKey("profile")
                ? TestProfile.parse(parsed.get("profile"))
                : harnessConfig.defaultProfile();
        final HarnessProfileSettings profileSettings = harnessConfig.profile(profile);
        final long randomSeed = parsed.containsKey("seed") ? Long.parseLong(parsed.get("seed")) : profileSettings.randomSeed();
        final long nowEpochSecond = parsed.containsKey("now-epoch-seconds")
                ? Long.parseLong(parsed.get("now-epoch-seconds"))
                : Instant.now().getEpochSecond();
        final boolean reset = Boolean.parseBoolean(parsed.getOrDefault("reset", "false"));
        final HarnessMode mode = parsed.containsKey("mode")
                ? HarnessMode.parse(parsed.get("mode"))
                : HarnessMode.SEED_VERIFY;
        if (reset && !mode.includesSeed()) {
            throw new IllegalArgumentException("--reset requires a harness mode that includes seeding");
        }

        final SeedPlan seedPlan = new SeedPlan(
                profile,
                profileSettings.playerCount(),
                randomSeed,
                profileSettings.exchangeTransactionCount(),
                profileSettings.storePurchaseCount(),
                profileSettings.entitlementGrantCount(),
                nowEpochSecond,
                reset
        );
        final int scenarioOperations = parsed.containsKey("operations")
                ? Integer.parseInt(parsed.get("operations"))
                : profileSettings.scenario().operations();
        final int scenarioConcurrency = parsed.containsKey("concurrency")
                ? Integer.parseInt(parsed.get("concurrency"))
                : profileSettings.scenario().concurrency();
        final long scenarioDurationSeconds = parsed.containsKey("duration-seconds")
                ? Long.parseLong(parsed.get("duration-seconds"))
                : profileSettings.scenario().durationSeconds();

        final Logger logger = Logger.getLogger("wild_economy_harness");
        final StringBuilder output = new StringBuilder(512);
        try (HarnessBootstrap.HarnessComponents components = HarnessBootstrap.create(configDirectory, logger)) {
            new HarnessGuard().validate(harnessConfig, components.databaseConfig(), seedPlan.resetFirst());

            if (mode.includesSeed()) {
                final SeedGenerator seedGenerator = new SeedGeneratorImpl(logger);
                final SeedRunReport seedRunReport = seedGenerator.generate(components, seedPlan);
                emit(output, seedRunReport.describe());
                final String breadthWarning = seedRunReport.catalogBreadthWarning(seedPlan.profile());
                if (breadthWarning != null) {
                    emit(output, breadthWarning);
                }
                final InvariantReport invariantReport = verifyDataset(components, seedPlan);
                emit(output, invariantReport.describe());
                if (!invariantReport.success()) {
                    flushReportFile(reportFile, output);
                    System.exit(2);
                }
            }

            if (mode.includesScenarios()) {
                final ScenarioRunner scenarioRunner = new ScenarioRunnerImpl();
                final ScenarioRunReport scenarioRunReport = scenarioRunner.run(
                        components,
                        seedPlan,
                        scenarioOperations,
                        scenarioConcurrency,
                        scenarioDurationSeconds,
                        profileSettings.scenario().mix()
                );
                printScenarioSummary(output, mode, scenarioRunReport);
                final InvariantReport invariantReport = verifyDataset(components, seedPlan);
                emit(output, invariantReport.describe());
                flushReportFile(reportFile, output);
                if (!invariantReport.success()) {
                    System.exit(2);
                }
                return;
            }

            flushReportFile(reportFile, output);
        }
    }

    private static InvariantReport verifyDataset(
            final HarnessBootstrap.HarnessComponents components,
            final SeedPlan seedPlan
    ) {
        final DatasetVerifier verifier = new DatasetVerifierImpl();
        return verifier.verify(components, seedPlan);
    }

    private static void printScenarioSummary(
            final StringBuilder output,
            final HarnessMode mode,
            final ScenarioRunReport runReport
    ) {
        emit(
                output,
                "Scenario run summary: mode=" + mode.name().toLowerCase()
                        + ", requestedOperations=" + runReport.requestedOperations()
                        + ", completedOperations=" + runReport.completedOperations()
                        + ", concurrency=" + runReport.concurrency()
                        + ", configuredDurationSeconds=" + runReport.configuredDurationSeconds()
                        + ", elapsedMillis=" + runReport.wallClockDurationMillis()
                        + ", stopReason=" + runReport.stopReason().name().toLowerCase()
                        + ", overallOpsPerSec=" + runReport.operationsPerSecond()
        );
        for (final ScenarioResult scenarioResult : runReport.scenarioResults()) {
            emit(output, " - " + scenarioResult.describe());
        }

        long totalOperations = 0L;
        long totalSuccesses = 0L;
        long totalExpectedRejections = 0L;
        long totalFailures = 0L;
        long totalDurationNanos = 0L;
        long maxDurationNanos = 0L;
        final Map<String, Long> rejectionReasons = new LinkedHashMap<>();
        final Map<String, Long> failureReasons = new LinkedHashMap<>();

        for (final ScenarioResult scenarioResult : runReport.scenarioResults()) {
            totalOperations += scenarioResult.operations();
            totalSuccesses += scenarioResult.successes();
            totalExpectedRejections += scenarioResult.expectedRejections();
            totalFailures += scenarioResult.failures();
            totalDurationNanos += scenarioResult.totalDurationNanos();
            maxDurationNanos = Math.max(maxDurationNanos, scenarioResult.maxDurationNanos());
            mergeReasonCounts(rejectionReasons, scenarioResult.rejectionReasons());
            mergeReasonCounts(failureReasons, scenarioResult.failureReasons());
        }

        final long averageMicros = totalOperations <= 0L ? 0L : (totalDurationNanos / totalOperations) / 1_000L;
        final long maxMicros = maxDurationNanos / 1_000L;
        emit(
                output,
                "Overall scenario totals: operations=" + totalOperations
                        + ", successes=" + totalSuccesses
                        + ", expectedRejections=" + totalExpectedRejections
                        + ", failures=" + totalFailures
                        + ", avgMicros=" + averageMicros
                        + ", maxMicros=" + maxMicros
                        + ", wallClockOpsPerSec=" + runReport.operationsPerSecond()
        );

        final String rejectionBreakdown = formatReasonBreakdown(rejectionReasons, 5);
        if (!rejectionBreakdown.isBlank()) {
            emit(output, "Expected rejection breakdown: " + rejectionBreakdown);
        }
        final String failureBreakdown = formatReasonBreakdown(failureReasons, 5);
        if (!failureBreakdown.isBlank()) {
            emit(output, "Failure breakdown: " + failureBreakdown);
        }
    }

    private static void mergeReasonCounts(final Map<String, Long> target, final Map<String, Long> source) {
        for (final Map.Entry<String, Long> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
    }

    private static String formatReasonBreakdown(final Map<String, Long> reasons, final int limit) {
        return reasons.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .limit(Math.max(1, limit))
                .map(entry -> entry.getKey() + " x" + entry.getValue())
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
    }

    private static void emit(final StringBuilder output, final String line) {
        System.out.println(line);
        output.append(line).append(System.lineSeparator());
    }

    private static void flushReportFile(final File reportFile, final StringBuilder output) {
        if (reportFile == null) {
            return;
        }
        try {
            final java.nio.file.Path path = reportFile.toPath();
            final java.nio.file.Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, output.toString(), StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to write harness report file '" + reportFile + "'", exception);
        }
    }

    private static Map<String, String> parseArgs(final String[] args) {
        final Map<String, String> parsed = new HashMap<>();
        for (int index = 0; index < args.length; index++) {
            final String raw = args[index];
            if (!raw.startsWith("--")) {
                continue;
            }
            final String key = raw.substring(2);
            final String value;
            if (index + 1 < args.length && !args[index + 1].startsWith("--")) {
                value = args[++index];
            } else {
                value = "true";
            }
            parsed.put(key, value);
        }
        return parsed;
    }
}
