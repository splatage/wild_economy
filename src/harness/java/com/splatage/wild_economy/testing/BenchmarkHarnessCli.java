package com.splatage.wild_economy.testing;

import com.splatage.wild_economy.bootstrap.HarnessBootstrap;
import com.splatage.wild_economy.testing.scenario.ScenarioResult;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class BenchmarkHarnessCli {

    private BenchmarkHarnessCli() {
    }

    public static void main(final String[] args) {
        final Map<String, String> parsed = parseArgs(args);
        final File configDirectory = new File(parsed.getOrDefault("config-dir", "src/main/resources"));
        final File harnessConfigFile = new File(parsed.getOrDefault("harness-config", "src/harness/resources/harness.yml"));
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

        final Logger logger = Logger.getLogger("wild_economy_harness");
        try (HarnessBootstrap.HarnessComponents components = HarnessBootstrap.create(configDirectory, logger)) {
            new HarnessGuard().validate(harnessConfig, components.databaseConfig(), seedPlan.resetFirst());

            if (mode.includesSeed()) {
                final SeedGenerator seedGenerator = new SeedGeneratorImpl(logger);
                final SeedRunReport seedRunReport = seedGenerator.generate(components, seedPlan);
                System.out.println(seedRunReport.describe());
                final InvariantReport invariantReport = verifyDataset(components, seedPlan);
                System.out.println(invariantReport.describe());
                if (!invariantReport.success()) {
                    System.exit(2);
                }
            }

            if (mode.includesScenarios()) {
                final ScenarioRunner scenarioRunner = new ScenarioRunnerImpl();
                final List<ScenarioResult> scenarioResults = scenarioRunner.run(
                        components,
                        seedPlan,
                        scenarioOperations,
                        scenarioConcurrency,
                        profileSettings.scenario().mix()
                );
                printScenarioSummary(mode, scenarioOperations, scenarioConcurrency, scenarioResults);
                final InvariantReport invariantReport = verifyDataset(components, seedPlan);
                System.out.println(invariantReport.describe());
                if (!invariantReport.success()) {
                    System.exit(2);
                }
            }
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
            final HarnessMode mode,
            final int operations,
            final int concurrency,
            final List<ScenarioResult> scenarioResults
    ) {
        System.out.println("Scenario run summary: mode=" + mode.name().toLowerCase() + ", operations=" + operations + ", concurrency=" + concurrency);
        for (final ScenarioResult scenarioResult : scenarioResults) {
            System.out.println(" - " + scenarioResult.describe());
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
