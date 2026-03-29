# wild_economy test harness

## Locked direction

The test system has two canonical parts:

1. **Seed-data generator** derived from the plugin's own runtime catalog, config, and persistence contracts.
2. **Load harness** that exercises real plugin paths against that seeded state.

This must **not** become a parallel economy model or a hand-maintained SQL universe.

## Fixed build order

1. Prefix-safe seed generator
2. Post-seed verifier
3. Service-path scenario runner
4. Command-path runner
5. Full client/UI swarm later

## Design rules

- Reuse existing config/model/repository/service contracts wherever possible.
- Honor configured table prefixes at all times.
- Refuse to run against dangerous prefixes unless explicitly forced.
- Deterministic by seed.
- Injectable clock / "now" value for reproducible recent-window testing.
- Support named profiles: `smoke`, `qa`, `perf`, `soak`.
- Emit a run summary and invariant report after each seed or scenario execution.

## Initial package layout

```text
src/main/java/com/splatage/wild_economy/testing/
  HarnessConfig.java
  HarnessMode.java
  HarnessGuard.java
  HarnessClock.java
  TestProfile.java

src/main/java/com/splatage/wild_economy/testing/seed/
  SeedPlan.java
  SeedRunReport.java
  SeedGenerator.java
  SeedGeneratorImpl.java
  SeedContext.java
  SeedDatasetBuilder.java
  SeedPlayerFactory.java
  SeedStockFactory.java
  SeedActivityFactory.java
  SeedSupplierAggregateFactory.java

src/main/java/com/splatage/wild_economy/testing/verify/
  InvariantCheck.java
  InvariantViolation.java
  InvariantReport.java
  DatasetVerifier.java
  DatasetVerifierImpl.java

src/main/java/com/splatage/wild_economy/testing/scenario/
  Scenario.java
  ScenarioContext.java
  ScenarioResult.java
  ScenarioRunner.java
  ScenarioRunnerImpl.java
  ScenarioMix.java
  ScenarioSelection.java

src/main/java/com/splatage/wild_economy/testing/scenario/impl/
  BrowseHeavyScenario.java
  BuyHeavyScenario.java
  SellHeavyScenario.java
  MixedEconomyScenario.java
  RestartRecoveryPlan.java

src/main/java/com/splatage/wild_economy/testing/report/
  HarnessReportWriter.java
  SeedSummaryWriter.java
  ScenarioSummaryWriter.java
  InvariantSummaryWriter.java
```

## Phase 1 scope

Phase 1 is **foundation only**. It does not yet attempt automated client swarms.

### Deliverables

- `HarnessConfig` with profiles and safety guardrails
- deterministic `SeedPlan`
- seed generator for player identities, balances, stock, recent activity, supplier aggregates
- post-seed invariant verifier
- service-path `ScenarioRunner`
- four scenario classes with weighted execution control
- summary reporting

## Seed model

### Players

Generated buckets:

- inactive
- casual
- active trader
- supplier-heavy
- wealthy outlier

Each player has:

- UUID
- name
- balance target band
- activity tendency
- preferred action mix

### Item state buckets

For each runtime catalog item, assign one of:

- unavailable
- scarce
- normal
- saturated
- hot
- cold

This gives realistic coverage for browse, buy, sell, and recent-category behavior.

### Activity windows

Timestamps must be generated relative to the injected clock:

- very recent: last 2 hours
- recent: last 24 hours
- warm: last 7 days
- old: older than recent-window

### Profiles

#### smoke
- 100 players
- full catalog coverage
- light stock variance
- minimal recent activity
- minimal transaction history

#### qa
- 2,000 to 5,000 players
- full catalog coverage
- meaningful hot/cold skew
- recent activity and supplier aggregates
- moderate logs

#### perf
- 20,000+ players
- concentrated hot-item contention
- heavy transaction history
- larger recent windows

#### soak
- perf-like dataset
- tuned for sustained repeated runs and restart validation

## Safety guardrails

The harness must refuse execution when:

- prefix is empty or obviously production-like
- run would target non-test schemas unexpectedly
- reset is requested without explicit harness enablement

Recommended config pattern:

```yaml
harness:
  enabled: false
  allow-reset: false
  required-prefix-marker: test_
  profiles:
    default: smoke
    smoke:
      player-count: 100
      random-seed: 1001
    qa:
      player-count: 3000
      random-seed: 2001
    perf:
      player-count: 25000
      random-seed: 3001
    soak:
      player-count: 25000
      random-seed: 4001
```

## Invariants

The verifier must at minimum assert:

- no negative balances
- no negative stock
- no oversell state on hot items
- recent-window queries only include rows inside the configured window
- weekly supplier totals contribute to all-time totals
- seeded rows use configured prefixes only
- item keys always resolve to canonical runtime keys

## Scenario system

### Service-path first

Phase 1 drives internal services directly because this gives clean signal before packet/UI noise.

### Scenario types

#### Browse-heavy
Exercises:
- top-level category resolution
- browse page generation
- curated category reads
- item detail reads

#### Buy-heavy
Exercises:
- repeated buys on hot items
- stock depletion
- same-item contention
- buy failures when stock is exhausted

#### Sell-heavy
Exercises:
- sell preview
- worth alias path
- sellall planning
- sellcontainer grouping and quoting

#### Mixed economy
Exercises:
- browse + buy + sell + supplier updates
- balance reads/writes
- curated recent reads
- aggregate refresh paths

## Scenario execution shape

Each run should declare:

- profile
- random seed
- start time
- duration or operation count
- weighted scenario mix
- concurrency
- report output path

Example model:

```yaml
run:
  profile: qa
  seed: 2001
  now-epoch-seconds: 1774600000
  operations: 100000
  concurrency: 16
  mix:
    browse-heavy: 40
    buy-heavy: 25
    sell-heavy: 25
    mixed-economy: 10
```

## Reporting

Every run should emit:

- seed summary
- scenario throughput summary
- latency percentiles per scenario/action
- invariant report
- failure sample summary

## Phase 2

After Phase 1 is stable:

- command-path harness
- automated restart/recovery test flow
- optional packet/client swarm layer
- benchmark scripts for CI or Jenkins

## What not to do

- no hand-maintained SQL fixtures as source-of-truth
- no duplicate pricing or catalog logic in the harness
- no direct table-name literals bypassing prefix policy
- no hidden production-safe assumptions
- no client-bot layer before service-path correctness is proven

## Next concrete patch slice

Patch in the following order:

1. `HarnessConfig`, `HarnessGuard`, `TestProfile`, `HarnessClock`
2. `SeedPlan`, `SeedRunReport`, `SeedGenerator`, `SeedGeneratorImpl`
3. `DatasetVerifier`, invariant model types
4. `Scenario`, `ScenarioRunner`, `ScenarioRunnerImpl`
5. service-path scenario implementations
6. report writers

This keeps the first slice clean, deterministic, and auditable.
