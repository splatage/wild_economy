# historical note

This document captures the **stage 1 problem map** from an earlier point in the architecture review.

It is still useful as historical context, but it is **not** the current architecture handoff.

Use these documents for the current state instead:

- `docs/wild_economy_architecture_audit_handoff.md`
- `docs/current_architecture_status_and_remaining_scope.md`

---

# wild_economy current architecture issue register (stage 1)

This document is the corrected current-repo issue register for the uploaded repository zip `wild_economy-main (5).zip`.

It is intentionally narrower and more current than `docs/wild_economy_architecture_audit_handoff.md`.
That handoff remains useful, but it was written against an earlier zip and now needs correction in a few places.

## Status of this document

This is **stage 1 only**:
- canonical problem map
- corrected issue list
- priority order
- target contracts
- remediation directions
- test implications

It is **not** the implementation plan for each code change yet.

---

## Priority order

1. Store purchase-flow durability gap
2. Migration ownership and stale migration residue
3. Canonical pricing interpolation duplication
4. Exchange/catalog dependence on gui.layout
5. layout.yml ownership/documentation contradiction
6. ServiceRegistry over-centralization
7. False headless seams in exchange services
8. Test coverage gaps in runtime-risk paths
9. Update the original handoff document after remediation decisions are locked

---

## Issue register

### 1. Store purchase flow has a concrete post-action durability gap

**Priority:** P0
**Severity:** Critical correctness / data integrity

**Affected files**
- `src/main/java/com/splatage/wild_economy/store/service/StoreServiceImpl.java`
- `src/main/java/com/splatage/wild_economy/persistence/TransactionRunner.java`
- `src/main/java/com/splatage/wild_economy/store/repository/StoreEntitlementRepository.java`
- `src/main/java/com/splatage/wild_economy/store/repository/StorePurchaseRepository.java`

**Current behavior**
- Money is withdrawn first.
- Product action is executed second.
- Durable purchase/entitlement persistence runs last in a DB transaction.
- If that last DB write fails, the player may already have been charged and granted the effect.

**Evidence**
- `StoreServiceImpl.purchase(...)` withdraws at lines 103-123.
- Product action executes at lines 124-144.
- Entitlement/purchase persistence is deferred until lines 146-169.
- `TransactionRunner` commits only the DB transaction it owns; it cannot atomically include already-executed economy/action side effects.

**Why it matters**
This is not merely “non-atomic” in a theoretical sense. It is a live post-action persistence gap.

**Target contract**
A successful store purchase must have one canonical success boundary. The system must never present “success” unless the durable store state that authorizes/audits that success is also secured under the chosen consistency model.

**Remediation direction**
Do not try to force a fake cross-system ACID transaction over Bukkit actions.
Instead, redesign the purchase flow around an explicit canonical state machine, for example:
- persist intent / pending purchase first
- reserve or charge money with clear compensation rules
- execute grant/action
- finalize durable success state
- make replay/reconciliation rules explicit

For permanent unlocks, entitlement persistence must become part of the canonical success path, not an afterthought after grant.

**Avoid**
- Silent best-effort writes after grant
- “Catch and log” persistence failures after side effects
- A second competing store-purchase pipeline

**Test implications**
Add tests for:
- DB failure after successful action execution
- refund/compensation path correctness
- duplicate retry behavior for permanent unlocks
- idempotency of grant vs entitlement persistence

---

### 2. Migration ownership is muddled and stale migration residue remains in the tree

**Priority:** P0
**Severity:** High correctness / operational drift

**Affected files**
- `src/main/java/com/splatage/wild_economy/persistence/MigrationDomain.java`
- `src/main/java/com/splatage/wild_economy/persistence/MigrationManager.java`
- `src/main/resources/db/migration/mysql/economy/V2__store_foundation.sql`
- `src/main/resources/db/migration/sqlite/economy/V2__store_foundation.sql`
- `src/main/resources/db/migration/mysql/V2__economy_core.sql`
- `src/main/resources/db/migration/sqlite/V2__economy_core.sql`
- `src/main/java/com/splatage/wild_economy/config/DatabaseConfig.java`

**Current behavior**
- Runtime config supports separate prefixes for economy, exchange, and store.
- Migration domains only model `EXCHANGE` and `ECONOMY`.
- Store tables are created by economy-domain migrations using `${store_prefix}` placeholders.
- Root-level migration files exist outside the active discovery path and differ materially from the active prefixed migrations.

**Evidence**
- `DatabaseConfig` contains `economyTablePrefix`, `exchangeTablePrefix`, and `storeTablePrefix`.
- `MigrationDomain` contains only `EXCHANGE` and `ECONOMY`.
- `MigrationManager.loadMigrations()` discovers only `db/migration/{dialect}/{domain}/...`.
- Active store foundation migration lives under `.../economy/V2__store_foundation.sql`.
- Root-level `db/migration/mysql/V2__economy_core.sql` and sqlite equivalent are outside active discovery and use unprefixed table names.

**Why it matters**
This creates two different kinds of drift:
- ownership drift: store has separate runtime naming but no explicit migration domain ownership
- operator/developer drift: stale root-level files can be mistaken for active migrations

**Target contract**
Migration ownership must match runtime domain ownership cleanly and unambiguously. The resource tree should contain only active, authoritative migration files.

**Remediation direction**
Decide explicitly whether store is:
- a true third migration domain, or
- intentionally owned by the economy domain

Then encode that decision clearly in naming, directory structure, and documentation.

Regardless of the domain decision, remove or quarantine the stale root-level migration files so they cannot be mistaken for active resources.

**Avoid**
- Leaving dead migrations in the active tree
- Introducing a store migration path while secretly still treating economy as authoritative
- Keeping two stories: separate prefixes at runtime, combined ownership in migrations, undocumented

**Test implications**
Add tests for:
- migration discovery returns only expected files
- schema-version table names align with domain/prefix ownership
- fresh bootstrap creates the intended prefixed tables only
- no stale root-level files are considered by discovery

---

### 3. Buy-envelope interpolation logic is duplicated

**Priority:** P1
**Severity:** High business-rule drift risk

**Affected files**
- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBrowseServiceImpl.java`
- `src/main/java/com/splatage/wild_economy/exchange/pricing/PricingServiceImpl.java`

**Current behavior**
- Browse/UI price display has its own local envelope interpolation helper.
- Authoritative pricing also has an interpolation helper.
- Both implementations are currently similar, but they are separate code paths.

**Evidence**
- `ExchangeBrowseServiceImpl.resolveCurrentBuyPrice(...)` and local `resolveEnvelopeUnitPrice(...)`
- `PricingServiceImpl.resolveBuyUnitPrice(...)`, `resolveSellUnitPrice(...)`, and canonical `resolveEnvelopeUnitPrice(...)`

**Why it matters**
This is one of the easiest ways to create user-visible pricing drift: displayed prices and charged prices diverge over time.

**Target contract**
There must be one canonical price interpolation rule for current envelope-derived unit price. Read/display paths may call into it, but may not re-implement it.

**Remediation direction**
Expose a read-side pricing API from the canonical pricing service, or extract a single pricing math component used by both browse and transaction paths.

**Avoid**
- A second “display pricing” helper beside authoritative pricing
- Copying the logic into GUI code or browse DTO builders

**Test implications**
Add tests that assert browse-displayed unit price equals authoritative quoted unit price across:
- below min stock
- within taper band
- above max stock
- degenerate envelope ranges

---

### 4. Exchange/catalog still depends on `gui.layout`

**Priority:** P1
**Severity:** High architectural boundary issue

**Affected files**
- `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogLoader.java`
- `src/main/java/com/splatage/wild_economy/exchange/catalog/ExchangeCatalogEntry.java`
- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBrowseServiceImpl.java`
- `src/main/java/com/splatage/wild_economy/gui/layout/*`

**Current behavior**
- Catalog loading depends on `LayoutPlacementResolver` from `gui.layout`.
- Catalog entries persist `layoutGroupKey` / `layoutChildKey` directly.
- Browse service depends on `LayoutBlueprint` and `LayoutChildDefinition` from `gui.layout`.

**Why it matters**
Layout is no longer just a GUI concern. It is part of the exchange/catalog navigation contract. Leaving it under `gui` makes ownership misleading and pushes presentation naming into application/domain code.

**Target contract**
The canonical browse/navigation layout contract should live in a neutral package that reflects shared ownership.

**Remediation direction**
Relocate the layout contract to a neutral package without changing behavior first. Only after that should any broader refactor be considered.

**Avoid**
- Building a parallel non-GUI browse model
- Splitting layout into GUI vs non-GUI variants
- Replacing the current contract before naming and ownership are settled

**Test implications**
Preserve and expand current layout loader/placement tests during relocation. Add tests that catalog placement and browse grouping still produce identical results after package move.

---

### 5. `layout.yml` ownership/documentation contradicts actual runtime usage

**Priority:** P1
**Severity:** Medium-high contract clarity issue

**Affected files**
- `src/main/java/com/splatage/wild_economy/config/ManagedConfigMaterializer.java`
- `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`
- `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogLoader.java`

**Current behavior**
- `ManagedConfigMaterializer` describes `layout.yml` as consumed directly by the GUI rather than the catalog generator.
- In reality, `ServiceRegistry` loads `layout.yml` before catalog loading.
- `CatalogLoader` uses layout placement resolution during catalog construction.

**Why it matters**
This is a contract/documentation bug, not just wording polish. The code and the project’s explanatory surface disagree about who owns layout.

**Target contract**
`layout.yml` ownership and role must be described exactly as implemented.

**Remediation direction**
Resolve this together with issue 4. Once layout ownership is decided, update `ManagedConfigMaterializer` and any related docs so they describe the true canonical path.

**Avoid**
- Treating this as documentation-only if the ownership model itself remains blurred

**Test implications**
None directly at logic level, but add a small config/bootstrap test if the project gains tests around managed config materialization.

---

### 6. `ServiceRegistry` remains oversized and over-centralized

**Priority:** P2
**Severity:** Medium-high structural risk

**Affected files**
- `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

**Current behavior**
`ServiceRegistry` is still a 678-line composition root that loads configs, builds persistence, runs migrations, constructs services, builds GUI, registers listeners and commands, schedules tasks, integrates Vault/PAPI, and handles shutdown.

**Why it matters**
The class is becoming a gravity well. The immediate risk is not aesthetics; it is that all future wiring, test harness setup, and cross-domain bootstrap decisions will accumulate here until the bootstrap path itself becomes the dominant hidden architecture.

**Target contract**
Keep one composition root, but break construction into a small number of explicit module assemblers returning typed bundles, not service locators.

**Remediation direction**
Defer this until correctness and ownership issues above are settled. Then split the registry along real seams:
- infrastructure/persistence bootstrap
- economy module
- exchange module
- store module
- GUI/bootstrap adapters

**Avoid**
- A second registry
- Ad hoc static singletons
- Letting services construct their own dependencies

**Test implications**
Add bootstrap-level tests around module factory outputs once seams exist.

---

### 7. Exchange services look more headless than they are

**Priority:** P2
**Severity:** Medium design-contract issue

**Affected files**
- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBuyService.java`
- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeBuyServiceImpl.java`
- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellService.java`
- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellServiceImpl.java`
- `src/main/java/com/splatage/wild_economy/exchange/service/FoliaSafeExchangeBuyService.java`
- `src/main/java/com/splatage/wild_economy/exchange/service/FoliaSafeExchangeSellService.java`

**Current behavior**
- Public exchange service interfaces accept `UUID`, which suggests a relatively headless application boundary.
- Implementations immediately resolve Bukkit `Player` objects and perform inventory, block, container, and protection interactions.
- Folia-safe wrappers also resolve Bukkit `Player` instances directly.

**Why it matters**
The issue is not “Bukkit usage is bad.” The issue is that the interface boundary suggests more purity than actually exists, which makes testing, orchestration, and future refactoring less honest.

**Target contract**
Either:
- these services are explicitly gameplay/Bukkit-facing orchestration services, or
- Bukkit interaction is pushed to adapters and the core sell/buy planner becomes truly headless.

A mixed story should be avoided.

**Remediation direction**
Do not rush this. First decide whether the desired architecture is adapter-backed headless planning or explicit gameplay services. Then reshape interfaces to match reality.

**Avoid**
- Parallel planner/service stacks
- Keeping the same misleading interface while moving only some logic out

**Test implications**
When the boundary is clarified, add tests for the headless portion only if such a portion is made explicit.

---

### 8. Tests remain too narrow for runtime-risk paths

**Priority:** P2, but attach to every remediation
**Severity:** Medium quality-risk issue

**Current behavior**
Tests currently cover:
- category classification
- derivation
- recipe fallbacks
- root value loading
- layout blueprint loading
- layout placement resolution

There is no meaningful automated coverage for:
- store purchase durability semantics
- exchange buy/sell gameplay paths
- migration ownership/discovery invariants
- pricing display vs authoritative quote consistency
- service/bootstrap assembly seams

**Target contract**
Critical invariants should be tested at the level where the project actually carries risk.

**Remediation direction**
Do not postpone tests until the end. Each architecture fix above should bring a focused test slice with it.

**Avoid**
- broad shallow coverage that misses the real fault lines
- a separate test-only model that does not use runtime code paths

---

## Working conclusion

The repo does not need a rewrite.

The current best direction is:
1. fix correctness and ownership seams first
2. collapse duplicate canonical rules
3. then split bootstrap structure along the now-stable boundaries
4. add focused tests with each change

The most important constraint remains the same as the original handoff:
**do not introduce parallel, competing, or shadow systems while fixing these issues.**
