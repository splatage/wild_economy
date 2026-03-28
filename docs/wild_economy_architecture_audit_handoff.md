# wild_economy architecture audit and remediation handoff

## Purpose

This document is a developer-facing handoff based on a static audit of the uploaded repository zip:

- `wild_economy-main (4).zip`

It is intended to help another developer address the architectural issues raised in the review without introducing parallel, competing, or fragmented systems.

## Audit constraints

This review was performed as a static code audit of the uploaded source tree.

One important limitation applies:

- The project was **not** compiled in this environment because the Gradle wrapper attempted to download Gradle from `services.gradle.org`, and outbound network access is blocked in the execution container.

So the findings below are based on direct source inspection, package mapping, file relationships, and code-path review, not on a successful local build or runtime traces.

## Source tree audited

The audit was performed against the extracted repository rooted at:

- `src/main/java/com/splatage/wild_economy/...`
- `src/main/resources/...`
- `src/test/java/...`

## Executive summary

The repository is **architecturally workable and mostly coherent**. It already has meaningful domain separation across:

- `economy`
- `exchange`
- `store`
- `catalog`
- `persistence`
- `config`
- `gui`

This is a good base. The code is **not currently fragmented into many competing systems**.

However, there are several areas where boundaries are beginning to blur. These are not all severe bugs, but they are exactly the kinds of drifts that can lead to duplicated logic, weak contracts, and fragile future work if not corrected now.

The highest-value concerns are:

1. `ServiceRegistry` has become too large and is taking on too many responsibilities.
2. `exchange` depends directly on `gui.layout`, which inverts the intended boundary.
3. `ExchangeBrowseServiceImpl` duplicates buy-envelope interpolation logic already present in `PricingServiceImpl`.
4. Buy/sell/store services are not truly headless application services; several are still Bukkit-bound orchestration surfaces.
5. Store purchase flow is compensating rather than atomic across money, action execution, and persistence.
6. Store tables have their own prefix but still live under the `ECONOMY` migration domain.
7. There are stale migration files that appear outside the active migration discovery path.
8. Test coverage is concentrated in catalog/layout logic and does not reach the critical runtime paths.

## Strengths worth preserving

These are important because remediation should build on them rather than replace them.

### 1. Prefix-aware persistence is already real

`DatabaseConfig` and the repository wiring already support separate prefixes for economy, exchange, and store. This is a strong architectural base and should remain canonical.

Relevant code:
- `src/main/java/com/splatage/wild_economy/config/DatabaseConfig.java`
- repository constructors throughout `ServiceRegistry.java`

### 2. Runtime exchange catalog is treated as canonical

The runtime exchange catalog is loaded and validated, rather than re-invented dynamically in multiple places.

Relevant code:
- `src/main/java/com/splatage/wild_economy/config/ConfigLoader.java`
- `src/main/java/com/splatage/wild_economy/config/ConfigValidator.java`
- `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogLoader.java`

This is the correct anchor for any future seed-data generator or harness.

### 3. Stock is cache-first with async persistence

`StockServiceImpl` is a strong foundation for performance-sensitive exchange state.

Relevant code:
- `src/main/java/com/splatage/wild_economy/exchange/stock/StockServiceImpl.java`

It already has:
- cache-first reads
- dirty tracking
- batched flush dispatch
- asynchronous persistence handling

A harness or future test tooling should reuse this path, not work around it.

### 4. Managed config generation is centralized

Managed config creation is centralized rather than scattered through individual loaders.

Relevant code:
- `src/main/java/com/splatage/wild_economy/config/ManagedConfigMaterializer.java`

That is exactly the right pattern and should remain the model for future config-owned features.

---

## Findings and remediation guidance

## Finding 1: `ServiceRegistry` is too large and too central

### Evidence

File:
- `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

Length:
- 678 lines

Notable scope in a single class:
- loads configs
- constructs database provider
- selects repositories by dialect
- runs migrations
- builds catalog
- validates config
- constructs stock/pricing/economy/store services
- constructs GUI menus
- registers listeners
- registers commands
- registers scheduled tasks
- tears everything down on shutdown

Representative lines:
- `initialize()` starts at line 185
- `registerCommands()` starts at line 525
- `registerTasks()` starts at line 599
- `shutdown()` starts at line 607

### Why it matters

This is still a composition root, so it is not wrong in principle. But it has become a gravity well.

The risk is not just size. The risk is that future work, especially test harness work, gets bolted into this class until the registry becomes the only place where anything can be wired. That makes the architecture harder to reason about and encourages hidden coupling.

### Safe remediation direction

Keep `ServiceRegistry` as the composition root, but split construction responsibilities into smaller, explicit assemblers or module builders.

A safe direction would be to introduce a small number of focused wiring helpers, for example:

- `EconomyModuleFactory`
- `StoreModuleFactory`
- `ExchangeModuleFactory`
- `GuiModuleFactory`
- `InfrastructureModuleFactory`

Each factory should return a small typed bundle of already-constructed services, not a service locator.

### Avoid

Do **not** solve this by introducing a second ad hoc registry, or by letting individual services construct their own dependencies.

That would create the fragmented, competing wiring model the project is trying to avoid.

### Recommended priority
- **High**

---

## Finding 2: `exchange` depends on `gui.layout`

### Evidence

File:
- `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogLoader.java`

Imports:
- `com.splatage.wild_economy.gui.layout.LayoutPlacement`
- `com.splatage.wild_economy.gui.layout.LayoutPlacementResolver`

Relevant lines:
- imports at lines 4-5
- resolver field at line 12
- resolver use at line 29

File:
- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBrowseServiceImpl.java`

Imports:
- `com.splatage.wild_economy.gui.layout.LayoutBlueprint`
- `com.splatage.wild_economy.gui.layout.LayoutChildDefinition`

Relevant lines:
- imports at lines 10-11
- field at line 22
- layout-driven browse methods at lines 34-107

### Why it matters

This is an architectural inversion.

`exchange` is now dependent on a package that is named as presentation-layer code: `gui.layout`.

That means layout is not really a GUI-only concern anymore. It has become part of the domain/application catalog contract. Leaving it in `gui` makes the boundary misleading and makes future non-GUI consumers awkward.

This matters especially for harness work, API clarity, and future reuse.

### Safe remediation direction

Move layout blueprint and placement concepts into a neutral package that reflects their actual role, for example:

- `exchange.layout`
- `catalog.layout`
- `navigation.layout`

The GUI can still consume them, but they should not appear to be owned by the GUI layer.

A pragmatic first step would be package relocation only, without changing behavior.

### Avoid

Do **not** fork layout logic into a second “headless browse model” just to keep testing code away from `gui`.

The right fix is to relocate the canonical layout contract, not duplicate it.

### Recommended priority
- **High**

---

## Finding 3: buy-envelope interpolation logic is duplicated

### Evidence

In `ExchangeBrowseServiceImpl`:

- `resolveCurrentBuyPrice(...)` at lines 161-176
- local `resolveEnvelopeUnitPrice(...)` at lines 178-203

File:
- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBrowseServiceImpl.java`

In `PricingServiceImpl`:

- buy path entry at lines 77-136
- canonical envelope helper at lines 235-255
- buy unit resolver at lines 199-207

File:
- `src/main/java/com/splatage/wild_economy/exchange/pricing/PricingServiceImpl.java`

### Why it matters

This is a classic drift risk.

Right now, browse price display and authoritative pricing both derive from similar logic, but through separate implementations. That means a future change can easily update one path without the other.

The visible result would be UI quotes that do not match actual transaction pricing.

### Safe remediation direction

Move the displayed current buy-price calculation behind a canonical pricing read API.

Examples:
- add a small read method to `PricingService`
- or introduce a neutral pricing projection helper used by both browse and transaction paths

The important point is that there should be **one** canonical interpolation implementation.

### Avoid

Do **not** copy the interpolation formula into another utility for tests or UI. That would deepen the problem.

### Recommended priority
- **High**

---

## Finding 4: service names imply headless domain services, but several are still Bukkit-bound orchestration surfaces

### Evidence

`StoreService` exposes:
- `StorePurchaseResult purchase(Player player, String productId);`

File:
- `src/main/java/com/splatage/wild_economy/store/service/StoreService.java`

`ExchangeBuyServiceImpl` is large and gameplay-bound:
- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBuyServiceImpl.java`
- 585 lines

`ExchangeSellServiceImpl` is large and gameplay-bound:
- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellServiceImpl.java`
- 798 lines

These services directly interact with Bukkit concepts such as:
- `Player`
- inventories
- containers
- item stacks
- live gameplay state

### Why it matters

The issue is not that Bukkit is used. The issue is API clarity.

A type called `StoreService` or `ExchangeSellService` sounds like an application/domain service that a harness or another application layer could call headlessly. In reality, these are partly:
- application orchestration
- Bukkit adapters
- gameplay interaction handlers

That makes testing and extension harder because the API contract is broader and less explicit than the name suggests.

### Safe remediation direction

Do not replace these services outright.

Instead, gradually extract pure internal planners or application operations from the Bukkit-heavy surfaces.

For example:
- keep the current Bukkit-facing service as the entry point
- extract pure quote/planning/decision logic into internal collaborators with non-Bukkit inputs and outputs
- let the Bukkit-facing service perform adaptation only

This preserves behavior while clarifying the contract.

### Avoid

Do **not** create a separate “test exchange service” or “headless store service” that reimplements the same rules. That would instantly create parallel systems.

### Recommended priority
- **High**, especially before large harness ambitions

---

## Finding 5: store purchase flow is compensating rather than atomic

### Evidence

File:
- `src/main/java/com/splatage/wild_economy/store/service/StoreServiceImpl.java`

Key flow in `purchase(...)`:
- withdraw: lines 103-121
- execute product action: line 124
- refund on action failure: lines 126-143
- persist entitlement/purchase inside transaction: lines 146-169

The structure is effectively:

1. charge money
2. execute action
3. if action fails, refund
4. if action succeeds, write entitlement and purchase record in a transaction

### Why it matters

This means store purchase is not one atomic cross-domain transaction.

That may be acceptable in practice because some actions cannot be transactionally coupled to SQL anyway. But it should be treated as an explicit design reality, not as if it were fully atomic.

The main architectural risk is that callers or future code assume “purchase” is one indivisible operation when it is really a compensating workflow.

### Safe remediation direction

Document the contract clearly and, where possible, tighten orchestration boundaries.

A good future direction would be:
- classify which store actions are safely reversible
- centralize purchase workflow states
- consider explicit purchase state progression if failures become more complex

But for now, clarity is more important than forced abstraction.

### Avoid

Do **not** hide this reality behind a misleading “transactional” abstraction if the action execution itself cannot participate.

### Recommended priority
- **Medium to High**

---

## Finding 6: store tables have separate prefixes, but migration ownership still treats store as part of the economy domain

### Evidence

Migration domains are defined as:
- `EXCHANGE`
- `ECONOMY`

File:
- `src/main/java/com/splatage/wild_economy/persistence/MigrationDomain.java`

There is no `STORE` migration domain.

Migration loading resolves resource directories from:
- `db/migration/sqlite/<domain>/`
- `db/migration/mysql/<domain>/`

File:
- `src/main/java/com/splatage/wild_economy/persistence/MigrationManager.java`
- lines 79-83

Store foundation SQL currently lives under the `economy` migration directory:

- `src/main/resources/db/migration/mysql/economy/V2__store_foundation.sql`
- `src/main/resources/db/migration/sqlite/economy/V2__store_foundation.sql`

The SQL correctly uses `${store_prefix}`, which is good, but ownership is still expressed through the `ECONOMY` domain.

### Why it matters

This is not a runtime correctness bug by itself. The schema will still be created with the right prefix.

The problem is conceptual ownership and future clarity.

The codebase direction says store should live in parallel with exchange, not as a sub-branch of economy. The migration model has not fully caught up with that conceptual separation.

### Safe remediation direction

There are two acceptable options:

#### Option A: keep store under economy intentionally
If the project decides that store is operationally part of the economy domain, document that explicitly and keep it consistent.

#### Option B: promote store to its own migration domain
Add:
- `STORE` to `MigrationDomain`
- separate migration directory roots
- separate schema version table prefix ownership if desired

Given the stated architectural direction, **Option B** is cleaner.

### Avoid

Do **not** duplicate migration logic or introduce a one-off “manual store schema bootstrap.” Migration ownership should remain canonical and centralized.

### Recommended priority
- **Medium**

---

## Finding 7: stale migration files appear outside the active discovery path

### Evidence

Active migration discovery uses:
- `db/migration/sqlite/<domain>/`
- `db/migration/mysql/<domain>/`

But these files exist at higher-level paths:

- `src/main/resources/db/migration/mysql/V2__economy_core.sql`
- `src/main/resources/db/migration/sqlite/V2__economy_core.sql`

Based on `MigrationManager`, these do not appear to be in the active discovery path.

### Why it matters

Dead or ambiguous migration files are dangerous.

Even if they are harmless today, they confuse future developers, invite mistaken assumptions about migration order, and make refactors riskier.

### Safe remediation direction

Verify whether these files are intentionally retained for history only or are accidental leftovers.

If they are not part of the active path:
- remove them, or
- relocate them to an archival docs location, or
- add a clear comment in repository documentation explaining that they are inactive historical residue

### Avoid

Do **not** leave ambiguous migration files in place indefinitely.

### Recommended priority
- **Medium**

---

## Finding 8: test coverage is narrow compared with the critical runtime paths

### Evidence

Current test files are:

- `src/test/java/com/splatage/wild_economy/catalog/classify/DefaultCategoryClassifierTest.java`
- `src/test/java/com/splatage/wild_economy/catalog/derive/RootAnchoredDerivationServiceTest.java`
- `src/test/java/com/splatage/wild_economy/catalog/recipe/RecipeGraphFallbacksTest.java`
- `src/test/java/com/splatage/wild_economy/catalog/rootvalue/RootValueLoaderTest.java`
- `src/test/java/com/splatage/wild_economy/gui/layout/LayoutBlueprintLoaderTest.java`
- `src/test/java/com/splatage/wild_economy/gui/layout/LayoutPlacementResolverTest.java`

There is no corresponding test coverage visible for:
- economy mutation correctness
- exchange stock persistence semantics
- supplier aggregate persistence
- exchange buy/sell orchestration
- store purchase orchestration
- migration domain/prefix correctness

### Why it matters

The tests currently focus on data-generation and layout logic, which is useful, but they do not reach the runtime-critical paths where correctness and coordination matter most.

This gap is part of why a seed-data generator and harness are now strategically important.

### Safe remediation direction

Do not try to solve this only with more unit tests inside the current Bukkit-heavy service surfaces.

Use a staged approach:
1. add a seed-data generator based on canonical runtime contracts
2. add a post-seed verifier for invariants
3. add service-path scenario execution where contracts are already clean
4. only then decide whether deeper service extraction is needed for broader automated coverage

### Avoid

Do **not** write a parallel simulation model just because current runtime paths are hard to unit test.

### Recommended priority
- **High**

---

## Finding 9: harness work must not be inserted directly into the existing runtime surface

### Evidence

This is a conclusion from the audit rather than a single-file bug.

The architecture already has useful canonical seams:
- config loaders
- canonical runtime catalog
- repositories
- stock service
- economy service
- migration/prefix model

But some gameplay services remain Bukkit-bound.

### Why it matters

If a harness is added carelessly, the likely failure mode is:

- extra seed logic inside runtime services
- fake pricing copies
- test-only SQL paths
- separate item-key normalization
- alternate browse models
- another hidden service layer

That would create exactly the fragmented code the project wants to avoid.

### Safe remediation direction

Harness work should live under a dedicated `testing` package and must reuse:
- canonical runtime catalog/config/schema contracts
- existing repository paths
- existing stock/economy services where feasible

For exchange/store flows that are still Bukkit-heavy, either:
- defer deep harnessing until a pure internal seam is extracted, or
- keep those scenarios narrower and explicit

### Avoid

Do **not** let the harness become a second economy or second exchange model.

### Recommended priority
- **Critical for future work**

---

## Recommended remediation order

This is the sequence that best preserves code elegance and avoids parallel systems.

### Phase 1: contract protection and drift prevention

1. Remove duplicated pricing interpolation by centralizing buy-price read logic.
2. Clarify layout ownership by moving layout contracts out of `gui`.
3. Clean stale migration files that are not on the active discovery path.
4. Document store purchase orchestration semantics as compensating, not fully atomic.

### Phase 2: composition-root cleanup

5. Split `ServiceRegistry` into focused module builders/factories while keeping one composition root.
6. Keep module outputs explicit and typed. Avoid service locators.

### Phase 3: headless seam extraction where actually needed

7. Extract pure planning/quote/orchestration helpers from Bukkit-heavy buy/sell/store services.
8. Preserve the existing Bukkit-facing services as adapters over the extracted logic.

### Phase 4: harness foundation

9. Build the seed-data generator from canonical runtime catalog/config/schema contracts.
10. Add a post-seed invariant verifier.
11. Add service-path scenario runners only at seams that are already canonical and non-duplicative.

---

## Safe remediation principles

Any developer addressing the issues above should follow these constraints:

- Do not invent a second pricing model.
- Do not invent a second catalog model.
- Do not invent test-only SQL as the source of truth.
- Do not fork store or exchange behavior into alternate “headless” services unless the original logic is being extracted cleanly.
- Do not let GUI-owned packages remain the hidden owner of non-GUI contracts.
- Do not treat migration ownership casually; keep it explicit and canonical.
- Keep one composition root, but make it smaller and more readable.

---

## Suggested follow-up tasks for the next developer

A sensible next implementation plan would be:

### Task 1
Refactor `PricingService` so browse-time displayed buy prices come from the same canonical interpolation path as transactional pricing.

### Task 2
Relocate layout contract classes out of `gui.layout` into a neutral package and update imports without changing behavior.

### Task 3
Audit migration resources and remove or archive inactive root-level migration files.

### Task 4
Draft a small design note defining whether store is:
- part of economy for migration ownership, or
- a true third migration domain

Then implement consistently.

### Task 5
Break `ServiceRegistry` into a small set of module builders while preserving the existing startup behavior.

### Task 6
Identify the first pure internal seam to extract from:
- `ExchangeBuyServiceImpl`
- `ExchangeSellServiceImpl`
- `StoreServiceImpl`

The goal is not to rewrite them, but to make future harness work possible without duplication.

---

## Closing judgement

The current codebase is in a **good enough architectural position to improve cleanly**.

It does **not** need a wholesale rewrite.

The main requirement is discipline: future work should tighten the existing canonical paths, not add parallel replacements beside them.

If the issues above are handled carefully, the repository can remain elegant, performant, and coherent while gaining the test harness and coverage it currently lacks.
