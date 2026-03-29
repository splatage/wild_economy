# wild_economy — Folia Support and Claim-Plugin Compatibility

Date: 2026-03-20
Project: `splatage/wild_economy`
Branch reviewed: `main`
Document type: scope / specification / implementation plan

---

## 1. Executive summary

This review covers two adjacent but distinct areas:

1. **Folia compatibility**
2. **Compatibility and integration with land-claim / protection plugins** such as GriefPrevention, PlotSquared, Towny, and Factions-style systems

### Current conclusion

`wild_economy` is presently a **normal Paper/Bukkit plugin**, not a Folia-ready plugin. It uses a classic Bukkit scheduler path, classic Bukkit player access patterns, and synchronous service/repository flows that have not yet been structured around Folia’s region-thread ownership model.

For claim/protection plugins, the current design appears to be mostly **passive coexistence** rather than **explicit integration**. Normal GUI use, buy/sell flows, and player-inventory operations are likely to coexist with protection plugins, but there is currently no visible first-class protection layer that intentionally checks claim permissions or territory rules.

The most protection-sensitive area is **container selling**, especially selling the contents of a looked-at chest/barrel/shulker or similar world container. That area should be treated as a distinct feature with its own access-control contract and its own plugin-integration layer.

---

## 2. Scope boundaries

This document defines:

* the current-state assessment
* the required architecture changes
* the proposed implementation shape
* phased rollout order
* acceptance criteria
* risks and open issues

This document does **not** attempt to fully redesign the entire economy plugin.

---

## 3. Current-state assessment

## 3.1 What the plugin currently looks like

The current repo structure and visible implementation indicate a normal Paper/Bukkit plugin with these broad characteristics:

* plugin bootstraps through a central `PluginBootstrap` / `ServiceRegistry`
* commands are registered centrally
* a repeating stock-turnover task is scheduled during enable
* exchange services directly work with Bukkit `Player`, `Inventory`, and `ItemStack`
* repository/service logging and stock mutation paths currently appear synchronous from the visible implementation
* there is no visible first-class protection/claim abstraction
* there are no visible direct integrations with Towny, PlotSquared, GriefPrevention, or Factions-family plugins

## 3.2 Current Folia state

The current implementation should be considered **not Folia-compatible as written**.

Reasons:

* plugin metadata does not currently declare Folia support
* the repeating turnover task is currently registered through the normal Bukkit scheduler path
* service code uses classic Bukkit access patterns such as `Bukkit.getPlayer(...)` and direct inventory mutation
* the runtime model has not yet been explicitly partitioned into:

  * region-thread-safe Bukkit/entity work
  * async persistence work
  * global-only work

## 3.3 Current claim/protection state

The current implementation should be considered **claim-plugin agnostic**, not claim-plugin integrated.

That means:

* it likely coexists fine for many ordinary use cases
* it does not appear to intentionally respect territory/plot/claim permission models
* it does not yet define what should happen in wilderness vs claim vs enemy land vs town spawn vs plot road etc.

## 3.4 Current code-level concerns observed during review

### A. Scheduler design is Bukkit/Paper style

The stock turnover job is currently wired as a normal repeating server task.

### B. Service code is still directly bound to Bukkit runtime objects

The visible buy/sell implementations are tightly coupled to `Player`, `Inventory`, Vault, and synchronous service calls. This is acceptable on standard Paper, but for Folia it needs a cleaner execution model.

### C. Persistence layer appears underdeveloped relative to the intended direction

The visible `AsyncExecutor` class is empty, while stock/log/database flows still appear to use direct calls. This means the code is not yet aligned with a robust off-main-thread persistence design.

### D. Container-selling implementation surface is currently inconsistent in the visible source snapshot

The command and interface surface clearly expect a `sellContainer(...)` capability. However, the visible implementation snapshot reviewed for `ExchangeSellServiceImpl` does not show a matching `sellContainer(...)` method. This inconsistency should be reconciled before doing deeper compatibility work.

### E. Some visible service signatures and lifecycle methods do not appear fully aligned

The visible source snapshot suggests some mismatch between the central wiring and the visible class/interface definitions. That needs to be cleaned up first so the branch becomes a stable source-of-truth before deeper adaptation work.

---

## 4. Design goals

## 4.1 Folia goals

1. Make the plugin safe and supportable on Folia.
2. Preserve a single plugin jar that still works on regular Paper.
3. Keep Bukkit/Folia thread-affinity rules explicit and localised.
4. Separate region-bound gameplay work from async persistence work.
5. Avoid broad behavioral drift in shop logic.

## 4.2 Claim/protection goals

1. Preserve passive coexistence with protection plugins.
2. Add an explicit protection-aware integration layer.
3. Define and enforce safe rules for `sellcontainer`.
4. Make protection handling configurable, not hardcoded.
5. Support the major claim plugin families incrementally.

---

## 5. Non-goals

* full marketplace redesign
* economy rebalance
* rewriting the GUI stack from scratch
* attempting to support every obscure claim plugin in v1
* introducing invasive per-plugin behavior in unrelated buy/sell flows unless clearly needed

---

## 6. Part A — Folia support

# 6.1 Problem statement

Folia requires code to respect region ownership and to stop assuming a single global main thread for gameplay actions. `wild_economy` currently behaves like a normal Paper plugin and therefore needs structured adaptation.

# 6.2 Folia support strategy

The recommended strategy is:

1. **Use Paper/Folia-native scheduler APIs**, not a Bukkit-only scheduler path.
2. Introduce a **small platform execution abstraction** inside the plugin.
3. Treat these as distinct execution categories:

   * **entity/player-bound work**
   * **location/block/container-bound work**
   * **global repeating tasks**
   * **async persistence/logging work**
4. Move business logic toward a shape where domain computation is separate from Bukkit object mutation.
5. Keep the plugin dual-targeted for Paper and Folia through the same code path where possible.

# 6.3 Required architecture changes

## 6.3.1 Add a platform scheduling/execution abstraction

Create an internal abstraction such as:

```java
public interface PlatformExecutor {
    void runGlobalRepeating(Runnable task, long initialDelayTicks, long periodTicks);
    void runGlobalNow(Runnable task);
    void runAsync(Runnable task);
    void runForPlayer(Player player, Runnable task);
    void runForLocation(World world, int blockX, int blockZ, Runnable task);
}
```

Suggested concrete implementations:

* `PaperPlatformExecutor`
* `FoliaPlatformExecutor`

Design intent:

* on normal Paper, this can delegate to the classic scheduler safely
* on Folia, this must delegate to the appropriate global/entity/region scheduler

## 6.3.2 Categorise all runtime operations by ownership type

### A. Global-only operations

Examples:

* periodic stock turnover trigger
* startup/shutdown orchestration
* non-region-specific maintenance

### B. Player-owned operations

Examples:

* reading/modifying player inventory
* delivering bought items
* removing sold items from inventory
* sending player messages in interaction flow

### C. Location/container-owned operations

Examples:

* reading a looked-at chest contents
* validating a block inventory
* scanning a container inventory in the world

### D. Async persistence operations

Examples:

* writing transaction logs
* persisting stock changes
* batch flush/snapshot tasks
* expensive DB I/O

## 6.3.3 Split domain planning from Bukkit mutation

Recommended shape for sell/buy flows:

1. gather state on the correct owner thread
2. produce an immutable plan/result object
3. apply Bukkit-side mutations on the correct owner thread
4. hand off persistence/logging to async infrastructure

Example:

```java
record PlannedSale(
    List<SlotSale> slotSales,
    double totalPayout,
    int totalItems
) {}
```

This makes the gameplay step easier to keep region-safe and easier to test.

## 6.3.4 Introduce a real async persistence layer

Current direction should move toward:

* bounded async executor(s)
* clear shutdown/flush lifecycle
* stock/log writes not performed inline on the player interaction thread unless intentionally required

Recommended minimum v1 structure:

* `PersistenceExecutor`
* `StockWriteQueue`
* `TransactionLogQueue`

Implementation options:

### Option A — simplest safe first pass

* one bounded async writer executor
* enqueue stock write operations
* enqueue transaction log operations
* flush on shutdown

### Option B — backend-aware later optimisation

* SQLite: single writer queue
* MySQL/MariaDB: small bounded pool + batching

## 6.3.5 Remove direct scheduler assumptions from service registry

`ServiceRegistry` should no longer directly call the Bukkit scheduler. Instead, it should receive or build the platform executor and register recurring work through that abstraction.

## 6.3.6 Add explicit Folia metadata

Once the plugin is actually ready, add Folia support declaration to plugin metadata.

Important note: metadata should be added **after** the actual runtime changes are in place, not before.

# 6.4 Folia-safe design rules

## 6.4.1 Player inventory rules

The following must happen on the owning player/entity thread:

* reading inventory contents when the result will drive mutation
* adding/removing items
* checking hand contents
* checking first empty slot if coupled to giving items

## 6.4.2 World container rules

The following must happen on the owning region thread:

* reading block state for a targeted container
* opening/reading a chest/barrel/shulker block inventory
* validating the block is still present and the same type
* removing items from that container

## 6.4.3 Async thread rules

The following must **not** happen on raw async threads:

* direct Bukkit entity mutation
* direct inventory mutation
* direct world/block/container mutation
* cross-region gameplay access without scheduler handoff

## 6.4.4 Cross-context workflow rule

If an operation needs both:

* a player inventory mutation
* a block container mutation

then that flow must be deliberately staged.

For example:

1. resolve and validate the container on the correct region
2. produce a sell plan
3. apply removal from container on the correct region
4. compute payout
5. deliver messages/effects via the player thread if needed
6. queue persistence async

# 6.5 Folia implementation scope

## 6.5.1 In scope

* add platform execution abstraction
* migrate turnover task registration
* make `sellhand`, `sellall`, and `buy` execution ownership-safe
* define and implement ownership-safe `sellcontainer`
* introduce async persistence executor/queues
* clean shutdown/flush semantics
* dual support for Paper + Folia

## 6.5.2 Out of scope for first Folia pass

* deep GUI refactor beyond what is required for thread safety
* major caching redesign unrelated to execution safety
* marketplace-specific work

# 6.6 Folia implementation plan

## Phase F0 — reconcile source-of-truth first

Before adaptation work:

* reconcile visible source inconsistencies
* ensure `sellContainer` implementation is present and agreed
* ensure service constructor signatures/lifecycle methods match
* ensure the branch builds cleanly from the actual source-of-truth

## Phase F1 — execution abstraction

Deliverables:

* `PlatformExecutor` interface
* Paper implementation
* Folia implementation
* `ServiceRegistry` migrated to use it
* turnover task migrated off direct Bukkit scheduler calls

## Phase F2 — player-bound flows

Deliverables:

* `sellHand` routed through player-owned execution
* `sellAll` routed through player-owned execution
* `buy` routed through player-owned execution
* no direct unsafe runtime assumptions in these paths

## Phase F3 — async persistence

Deliverables:

* real async executor
* transaction logging queue
* stock persistence queue or controlled async stock write path
* lifecycle flush on disable

## Phase F4 — container flows

Deliverables:

* actual `sellContainer` implementation finalised
* block/container access region-safe
* clear separation between held-shulker selling and world-container selling

## Phase F5 — validation and release hardening

Deliverables:

* Paper regression pass
* Folia manual validation matrix
* plugin metadata updated to mark support
* admin documentation updated

# 6.7 Folia acceptance criteria

The Folia work is complete when:

1. the plugin can start cleanly on Folia
2. no normal scheduler assumptions remain in active gameplay paths
3. `/shop`, `/sellhand`, `/sellall`, and `/buy` execute without thread-ownership violations
4. `sellcontainer` executes without thread-ownership violations
5. DB/logging work is no longer tightly coupled to gameplay thread execution
6. shutdown drains/flushes async work cleanly
7. the plugin still works correctly on regular Paper

# 6.8 Folia test matrix

## Baseline tests

* plugin enable/disable on Paper
* plugin enable/disable on Folia
* turnover task runs correctly
* no scheduler exceptions on startup or shutdown

## Gameplay tests

* `/sellhand`
* `/sellall`
* basic `/shop` browsing
* basic buy flow
* buy with full inventory
* sell with invalid items
* rapid repeated buy/sell spam

## Container tests

* sell from looked-at chest
* sell from looked-at barrel
* sell held shulker item
* denied/invalid container
* container changed while action is in progress

## Stress tests

* multiple players buying/selling concurrently
* turnover firing while active buy/sell traffic occurs
* plugin disable during pending queued writes

---

## 7. Part B — Claim/protection compatibility

# 7.1 Problem statement

The plugin currently has no visible first-class protection integration. For ordinary GUI and player-inventory actions this may be acceptable, but for world container interactions it is not sufficient.

The plugin needs an explicit policy for how shop actions should behave inside claims/plots/towns/faction land.

# 7.2 Claim compatibility strategy

The recommended strategy is:

1. Define a **neutral protection integration interface** inside `wild_economy`.
2. Implement a **Noop provider** for servers with no claim plugin.
3. Add **first-class adapters** for the most important ecosystems first.
4. Treat `sellcontainer` as the primary protection-sensitive feature.
5. Keep policy configurable.

# 7.3 Core functional distinction

There are two very different container-selling cases:

## A. Held-container selling

Example:

* a shulker box item in the player’s own hand/inventory

This is mostly a **player-inventory** problem, not a land-protection problem.

## B. World-container selling

Example:

* a looked-at chest, barrel, trapped chest, hopper, or placed shulker box block

This is a **land/container access** problem and must respect claim/plot/town/faction permissions.

These must be treated as different code paths with different policy checks.

# 7.4 Protection-aware requirements

## 7.4.1 Baseline coexistence requirements

These should work even without explicit plugin integrations:

* `/shop`
* `/sellhand`
* `/sellall`
* browsing categories
* buying to player inventory

## 7.4.2 Protection-gated requirements

These must go through a protection layer:

* selling contents of a looked-at world container
* any future feature that inspects or mutates placed block inventories
* any future feature that opens protected stock sources in the world

## 7.4.3 Optional policy-gated requirements

These may optionally be restricted by claim context depending on config:

* opening `/shop` in enemy territory
* buying in combat/war zones
* using `/sellall` inside certain protected areas
* using `/shop` in plots/roads/spawn only

# 7.5 Protection integration architecture

## 7.5.1 Core interface

Recommended abstraction:

```java
public interface ProtectionIntegration {
    String getName();

    boolean isAvailable();

    ProtectionDecision canUseShop(Player player, ShopActionContext context);

    ProtectionDecision canSellContainer(Player player, ContainerSellContext context);
}
```

`ProtectionDecision` example:

```java
public record ProtectionDecision(
    boolean allowed,
    String denialReasonKey,
    String providerName
) {}
```

## 7.5.2 Context models

Suggested context types:

```java
public enum ShopActionType {
    OPEN_SHOP,
    BUY,
    SELL_HAND,
    SELL_ALL,
    SELL_CONTAINER_HELD,
    SELL_CONTAINER_BLOCK
}
```

```java
public record ShopActionContext(
    ShopActionType actionType,
    @Nullable World world,
    @Nullable Location location
) {}
```

```java
public record ContainerSellContext(
    ShopActionType actionType,
    World world,
    Location location,
    Material containerType
) {}
```

## 7.5.3 Manager / dispatcher

Add a `ProtectionManager` that:

* detects installed integrations
* loads configured provider order
* routes checks to the appropriate adapter(s)
* returns a final allow/deny result

Two possible models:

### Model A — single active provider

Good when the server has exactly one land plugin.

### Model B — aggregate veto model

Useful when multiple systems may coexist. For v1, this may be unnecessary complexity.

Recommended starting point: **single active provider selected by configured or detected priority**.

## 7.5.4 Fallback provider

Implement `NoProtectionIntegration` which always allows actions.

This preserves normal behavior on servers without claim plugins.

# 7.6 Recommended plugin support order

## Tier 1 — first-class support

1. **GriefPrevention**
2. **PlotSquared**
3. **Towny**

Reason:

* common and actively used
* clearer access models
* directly relevant to container access and player territory rules

## Tier 2 — important but fragmented

4. **Factions-family support via bridge or adapter strategy**

Reason:

* “Factions” is ecosystem-fragmented
* direct one-off integrations may create maintenance burden
* a bridge strategy is preferable where possible

## Tier 3 — later extensibility

* Lands
  n- HuskClaims
* Residence
* WorldGuard-based policy gates if desired

# 7.7 Protection policy specification

## 7.7.1 Default recommended policies

### `/shop`

Default: **allowed everywhere**

Reason:

* pure GUI/economy access
* avoids unnecessary friction

### `/sellhand`

Default: **allowed everywhere**

Reason:

* only affects player-held item

### `/sellall`

Default: **allowed everywhere**

Reason:

* only affects player inventory

### `sellcontainer` for held shulker item

Default: **allowed everywhere**

Reason:

* inventory-owned item, not world access

### `sellcontainer` for world container block

Default: **deny unless protection adapter confirms access**

Reason:

* world interaction with strong grief/abuse potential

## 7.7.2 Optional stricter policies

Expose config switches to optionally restrict:

* shop opening in enemy territory
* shop use in war zones
* selling from containers only if player has explicit container trust
* selling from containers only if player could normally open that container through the protection plugin

## 7.7.3 Denial semantics

When denied:

* do not partially mutate inventory/container contents
* return a clear player-facing message
* include plugin/provider-specific reason when useful

# 7.8 Plugin-specific behavioral notes

## 7.8.1 GriefPrevention

Need to distinguish:

* wilderness
* claimed area
* trust/container access

Desired baseline rule:

* allow world-container selling only if the player has the equivalent of legitimate container access in that claim

## 7.8.2 PlotSquared

Need to distinguish:

* owned plot
* added/trusted player
* unowned plot
* road
* spawn/plot world special cases

Desired baseline rule:

* allow world-container selling only if the player has the equivalent of legitimate interaction/container access in that plot context

## 7.8.3 Towny

Need to distinguish:

* wilderness
* town block
* resident-owned area / plot
* town spawn / restricted areas
* enemy/war rules if relevant to the server’s config

Desired baseline rule:

* allow world-container selling only if the player has the equivalent of legitimate container/use permissions for that location

## 7.8.4 Factions-family systems

Need to distinguish:

* own faction land
* ally/truce land
* enemy land
* wilderness
* safezone/warzone

Desired baseline rule:

* do not hardcode assumptions about every fork
* prefer bridge/provider abstraction where possible
* make wilderness/safezone/warzone behavior configurable

# 7.9 Container resolution specification

## 7.9.1 World container target resolution

Add `ContainerTargetResolver` with responsibilities:

* determine whether player is targeting a valid block container
* validate distance and line-of-sight rules
* resolve supported container types
* provide immutable target result

Suggested result shape:

```java
public record ContainerTargetResult(
    World world,
    Location location,
    Material containerType,
    boolean valid,
    String failureReasonKey
) {}
```

## 7.9.2 Held-container resolution

Add separate handling for held shulkers:

* check main hand or explicitly selected held item
* inspect item meta / block-state meta safely
* ensure protected shulker behavior remains in force
* avoid treating a held shulker as a world container

## 7.9.3 Atomicity rule

For any container sale:

* validate container access first
* scan/planning second
* commit removal third
* payout fourth
* persistence/logging last

No partial success should occur if the protection check fails.

# 7.10 Claim integration configuration

Suggested config structure:

```yaml
compatibility:
  folia:
    enabled: true

  protection:
    enabled: true
    provider-order:
      - griefprevention
      - plotsquared
      - towny
      - factionsbridge
      - none

    shop:
      allow-open-everywhere: true
      restrict-buy-by-territory: false
      restrict-sellhand-by-territory: false
      restrict-sellall-by-territory: false

    sellcontainer:
      enabled: true
      allow-held-shulker-everywhere: true
      require-protection-check-for-world-containers: true
      allowed-world-container-types:
        - CHEST
        - BARREL
        - TRAPPED_CHEST
        - SHULKER_BOX
        - HOPPER

      deny-if-provider-unknown: false
      deny-in-enemy-territory: true
      deny-in-safezone: false
      deny-in-warzone: true

    messages:
      denied-shop-use: "You cannot use the shop here."
      denied-sellcontainer: "You cannot sell from that container here."
      provider-blocked: "Action blocked by protection rules."
```

# 7.11 plugin.yml / dependency recommendations

## 7.11.1 plugin.yml

Recommended future additions:

* Folia support metadata when truly ready
* soft-dependencies for supported protection plugins

Suggested direction:

```yaml
softdepend:
  - Essentials
  - GriefPrevention
  - PlotSquared
  - Towny
  - FactionsBridge
```

Note:

* exact plugin names must match actual runtime plugin names for the integrations chosen
* do not add plugin names speculatively without validating the exact API/plugin id

## 7.11.2 Gradle/API dependencies

Use `compileOnly` for optional integrations.

Recommended rule:

* only add compile-time APIs for plugins we intentionally support
* do not hard-link the plugin to optional integrations at runtime

# 7.12 Claim integration implementation phases

## Phase C0 — reconcile `sellcontainer`

Before claim work proceeds:

* finalise the actual `sellContainer` implementation
* explicitly split held-shulker vs world-container flows
* ensure branch source-of-truth is internally consistent

## Phase C1 — protection scaffolding

Deliverables:

* `ProtectionIntegration`
* `ProtectionDecision`
* `ProtectionManager`
* `NoProtectionIntegration`
* configuration skeleton
* message keys

## Phase C2 — protection-aware world container gating

Deliverables:

* protection check inserted into world-container sell flow
* no checks added to held-shulker path beyond inventory protections
* clean player denial messages

## Phase C3 — first-class adapters

Deliverables:

* GriefPrevention adapter
* PlotSquared adapter
* Towny adapter

## Phase C4 — factions strategy

Deliverables:

* preferred bridge-based adapter if practical
* otherwise clearly scoped best-effort adapter(s)
* territory policy config

## Phase C5 — optional territory rules for ordinary shop usage

Deliverables:

* configurable restrictions for `/shop`, buy, sellhand, sellall if desired
* defaults remain permissive unless server owner opts in

# 7.13 Claim acceptance criteria

Claim integration is complete when:

1. the plugin still works normally without any protection plugin installed
2. ordinary `/shop`, `/sellhand`, and `/sellall` flows remain stable
3. world-container selling is denied when protection rules say no
4. world-container selling is allowed when protection rules say yes
5. held-shulker selling is clearly separated from world-container selling
6. denial never causes partial item removal or partial payout
7. supported plugin adapters behave consistently with their host plugin’s permission model

# 7.14 Claim test matrix

## No protection plugin

* `/shop` works
* `/sellhand` works
* `/sellall` works
* held shulker sell works
* world-container sell follows fallback policy

## GriefPrevention

* wilderness allowed/denied according to config
* own claim allowed
* trusted claim allowed
* untrusted claim denied
* no partial sell on deny

## PlotSquared

* own plot allowed
* trusted plot allowed
* non-member plot denied
* road behavior matches config
* no partial sell on deny

## Towny

* wilderness behavior matches config
* own town plot allowed
* no-permission plot denied
* spawn/restricted-area behavior matches config

## Factions-family

* own land allowed
* ally/truce behavior matches config
* enemy land denied if configured
* wilderness/safezone/warzone behavior matches config

---

## 8. Cross-cutting implementation recommendations

# 8.1 Recommended order of work

1. **Reconcile current branch inconsistencies**
2. **Implement Folia execution abstraction**
3. **Migrate scheduler and player-bound flows**
4. **Introduce async persistence execution**
5. **Finalise `sellcontainer` semantics**
6. **Add protection manager and Noop provider**
7. **Add GP / PlotSquared / Towny adapters**
8. **Add Factions bridge strategy**
9. **Run Paper + Folia + protection-plugin validation matrix**

# 8.2 Strong recommendation on first practical milestone

The best first milestone is:

> make the existing plugin internally consistent and Folia-safe before deep claim integrations.

Reason:

* `sellcontainer` is the highest-risk claim feature
* that same feature is also the most region-sensitive Folia feature
* solving it cleanly once gives a strong foundation for both areas

# 8.3 Recommended internal package additions

Suggested packages:

```text
com.splatage.wild_economy.platform
com.splatage.wild_economy.platform.paper
com.splatage.wild_economy.platform.folia
com.splatage.wild_economy.persistence
com.splatage.wild_economy.compat.protection
com.splatage.wild_economy.compat.protection.impl
com.splatage.wild_economy.exchange.container
```

---

## 9. Open issues / blockers

## 9.1 Source snapshot inconsistency around `sellContainer`

The currently visible source snapshot strongly suggests the command and interface surface expect `sellContainer(...)`, but the reviewed implementation snapshot does not visibly contain it. This must be resolved first.

## 9.2 Source snapshot inconsistency around service constructors/lifecycle methods

The visible central wiring and visible class/interface definitions do not appear fully aligned. This should be normalised before further compatibility work.

## 9.3 Need a final policy decision for world-container selling

The server owner needs a clear default policy for:

* wilderness
* own claim
* trusted claim
* neutral claim
* enemy land
* roads/spawn/safezone/warzone style areas

## 9.4 Need a final decision on factions integration strategy

Because the Factions ecosystem is fragmented, the project should explicitly decide whether to:

* support one specific faction plugin directly
* support a bridge
* or defer factions support to phase 2

---

## 10. Final recommended scope statement

### Folia scope statement

Implement a dual-targeted Paper/Folia execution model for `wild_economy` by introducing a platform executor abstraction, migrating scheduler use, ensuring player/container actions execute on the correct owner threads, and moving persistence/logging onto explicit async infrastructure. Do not mark the plugin Folia-supported until all active gameplay paths are validated.

### Claim compatibility scope statement

Implement a protection-aware compatibility layer for `wild_economy` with a neutral internal API, a fallback no-protection provider, and first-class adapters for GriefPrevention, PlotSquared, and Towny. Treat world-container selling as a protection-gated feature with clear atomicity rules and configurable policy. Handle Factions-family support through a bridge or a carefully scoped later phase.

---

## 11. Recommended deliverable breakdown

## Deliverable 1 — branch reconciliation

* clean source-of-truth
* compile/build sanity
* agreed `sellcontainer` implementation surface

## Deliverable 2 — Folia foundation

* platform executor
* scheduler migration
* player-bound flow safety

## Deliverable 3 — persistence safety

* async logging
* async stock writes or controlled write queue
* shutdown flush

## Deliverable 4 — container selling foundation

* target resolver
* held vs world container split
* atomic plan/commit flow

## Deliverable 5 — protection integration foundation

* protection manager
* no-op provider
* config + messages

## Deliverable 6 — first-class claim adapters

* GriefPrevention
* PlotSquared
* Towny

## Deliverable 7 — factions strategy

* bridge or plugin-specific adapter
* territory policy handling

---

## 12. Bottom line

The plugin is **not yet ready for Folia** and is **not yet explicitly compatible with major claim systems beyond passive coexistence**.

The good news is that both problems can be solved cleanly with one coherent approach:

* make execution ownership explicit
* finalise safe `sellcontainer` semantics
* introduce a formal protection integration layer

That approach preserves the existing design direction while hardening the plugin for modern Paper/Folia deployment and for real survival servers that rely on land-protection plugins.

---

## Appendix A — Precise Folia remediation spec by file/class

This appendix translates the Folia analysis into a concrete remediation specification by class. It is intentionally implementation-facing.

### A.1 Design principle for the refactor

The plugin should be reorganized around four execution domains:

1. **Global/plugin-owned execution**

   * repeating turnover tasks
   * startup/shutdown orchestration
   * plugin-owned cache maintenance

2. **Player/entity-owned execution**

   * inventory reads that drive immediate gameplay actions
   * inventory mutation
   * opening/closing inventories
   * player messages tightly coupled to gameplay mutations
   * held-shulker inspection and replacement

3. **Region/location-owned execution**

   * looked-at block resolution
   * block state reads
   * world container inventory reads and writes
   * any future placed-container interaction

4. **Async persistence execution**

   * transaction log writes
   * stock persistence flushes
   * any database/network work not requiring Bukkit world/entity access

The refactor should make every affected class obey one of these ownership rules explicitly.

---

## A.2 New internal support classes to add first

### `com.splatage.wild_economy.platform.PlatformMode`

Purpose:

* represent whether the plugin is running on Paper or Folia

Responsibilities:

* detect Folia using a runtime class check
* expose a simple enum or boolean to bootstrap code

### `com.splatage.wild_economy.platform.PlatformExecutor`

Purpose:

* centralise all scheduler/execution routing

Required API surface:

* `void runGlobalNow(Runnable task)`
* `void runGlobalRepeating(Runnable task, long delayTicks, long periodTicks)`
* `void cancelPluginTasks()`
* `void runAsync(Runnable task)`
* `void runForPlayer(Player player, Runnable task)`
* `void runForLocation(Location location, Runnable task)`

Optional but recommended:

* `CompletableFuture<T> supplyForPlayer(Player player, Supplier<T> supplier)`
* `CompletableFuture<T> supplyForLocation(Location location, Supplier<T> supplier)`

Hard rules:

* `runForPlayer` must use the entity scheduler on Folia and must not be implemented via region scheduler
* `runForLocation` must use the region scheduler on Folia and must not be used for entity-owned work
* on regular Paper, the same interface can delegate to equivalent scheduler behavior

### `com.splatage.wild_economy.platform.paper.PaperPlatformExecutor`

Purpose:

* implement the `PlatformExecutor` contract on normal Paper

Responsibilities:

* use the Paper/Folia-compatible scheduler APIs where possible
* preserve identical logical ownership boundaries even though Paper is single-threaded in practice

### `com.splatage.wild_economy.platform.folia.FoliaPlatformExecutor`

Purpose:

* implement the `PlatformExecutor` contract on Folia

Responsibilities:

* global work -> global region scheduler
* player/entity work -> entity scheduler
* location/container work -> region scheduler
* async persistence -> async scheduler or controlled executor handoff

### `com.splatage.wild_economy.exchange.container.ContainerTargetResolver`

Purpose:

* isolate target-block resolution from sell logic

Responsibilities:

* resolve the player’s looked-at block container
* validate supported container types
* return an immutable result describing the target
* never perform payout or sale logic

### `com.splatage.wild_economy.exchange.container.ContainerSellPlanner`

Purpose:

* isolate planning from mutation

Responsibilities:

* inspect an `Inventory`
* produce immutable planned sales and skipped reasons
* avoid direct Bukkit side effects other than reading inventory contents

### `com.splatage.wild_economy.gui.MenuSessionStore`

Purpose:

* replace direct shared `HashMap` usage

Responsibilities:

* thread-safe storage for menu session snapshots
* `put`, `get`, `remove`
* no GUI opening logic

Required implementation shape:

* use `ConcurrentHashMap<UUID, MenuSession>` at minimum
* do not expose raw mutable internals

---

## A.3 `ServiceRegistry` — exact Folia responsibilities

### Current issues

Current class behavior includes:

* building all services
* wiring GUI classes
* registering event listeners
* registering commands
* registering turnover tasks through the Bukkit scheduler
* cancelling tasks through the Bukkit scheduler

That mixes bootstrap concerns with non-Folia-safe task registration.

### Required new responsibilities

`ServiceRegistry` should become the **composition root only**.

It should:

* detect platform mode
* create the correct `PlatformExecutor`
* inject that executor into the classes that need it
* register listeners and commands
* start recurring work through the `PlatformExecutor`
* shut down async/plugin-owned infrastructure cleanly

It should **not**:

* call `plugin.getServer().getScheduler().runTaskTimer(...)`
* call `plugin.getServer().getScheduler().cancelTasks(...)`
* perform gameplay logic directly

### Constructor / field changes

Add fields for:

* `PlatformExecutor platformExecutor`
* optionally `MenuSessionStore menuSessionStore`

Inject `PlatformExecutor` into at least:

* `ShopMenuRouter`
* `ExchangeBuyServiceImpl`
* `ExchangeSellServiceImpl`
* optionally GUI menu classes if they will schedule their own reopen/refresh flows

### `initialize()` contract after refactor

`initialize()` should:

1. load config and repositories
2. build persistence and stock/log services
3. build `PlatformExecutor`
4. build domain services (`browse`, `buy`, `sell`, `exchange`)
5. build `MenuSessionStore`
6. build GUI/router/listener classes with injected dependencies
7. register listeners

### `registerTasks()` contract after refactor

Must schedule turnover using the platform executor as **global/plugin-owned work**.

Required rule:

* turnover must be scheduled through the global-compatible path, not the Bukkit scheduler directly

### `shutdown()` contract after refactor

Must:

* cancel platform-owned repeating tasks through `PlatformExecutor`
* shut down transaction log service
* shut down stock service
* close DB provider

Must not:

* call Bukkit scheduler cancellation directly

### Acceptance criteria for `ServiceRegistry`

* no direct Bukkit scheduler usage remains in this class
* all execution-sensitive services receive their executor dependencies
* startup and shutdown behave correctly on both Paper and Folia

---

## A.4 `ShopMenuRouter` — exact Folia responsibilities

### Current issues

Current class:

* stores sessions in a plain `HashMap`
* updates session state
* opens inventories immediately after mutating shared session state
* acts as both state manager and UI navigation controller

Under Folia, this is a concurrency risk because multiple players may interact in parallel on different region/entity threads.

### Required responsibility split

`ShopMenuRouter` should remain the **navigation coordinator**, but not the raw session store.

It should:

* depend on `MenuSessionStore`
* create immutable `MenuSession` snapshots
* decide which menu to open next
* invoke menu opens through player-owned execution semantics

It should not:

* own a plain `HashMap`
* rely on unsynchronised shared mutable session state

### Required structural changes

1. Replace:

   * `private final Map<UUID, MenuSession> sessions = new HashMap<>();`
     with injected `MenuSessionStore`

2. Add injected `PlatformExecutor`

3. Every method that both:

   * updates session state
   * opens a player inventory
     should execute as **player-owned work**

### Method-by-method spec

#### `openRoot(Player player)`

Responsibilities after refactor:

* create `MenuSession(ROOT, ...)`
* store it via `MenuSessionStore`
* invoke `exchangeRootMenu.open(player)` on player-owned execution

#### `openSubcategory(Player player, ItemCategory category)`

Responsibilities after refactor:

* create `SUBCATEGORY` session snapshot
* store snapshot
* invoke subcategory menu open on player-owned execution

#### `openBrowse(Player player, ItemCategory category, GeneratedItemCategory generatedCategory, int page, boolean viaSubcategory)`

Responsibilities after refactor:

* create `BROWSE` session snapshot
* store snapshot
* invoke browse menu open on player-owned execution

#### `openDetail(Player player, ItemKey itemKey)`

Responsibilities after refactor:

* read prior session snapshot
* derive the new detail session snapshot while preserving browse context
* store snapshot
* invoke detail menu open on player-owned execution

#### `goBack(Player player)`

Responsibilities after refactor:

* read current session snapshot
* compute target destination view
* call the appropriate `open...` method

Important rule:

* `goBack` should not directly mutate inventory state itself; it should continue to route via the menu open methods

#### `getSession(UUID)` / `clearSession(UUID)`

Responsibilities after refactor:

* delegate to `MenuSessionStore`

### Acceptance criteria for `ShopMenuRouter`

* no plain `HashMap` remains
* session state is safe for concurrent multi-player access
* all inventory opening calls are ultimately routed as player-owned work
* navigation semantics remain unchanged

---

## A.5 `ShopMenuListener` — exact Folia responsibilities

### Current issues

Current class:

* reads shared session state directly from router
* dispatches GUI clicks directly to menu classes
* clears session on quit

The event origin is generally already player-owned, which is good, but the session store must still be made safe.

### Required responsibilities after refactor

`ShopMenuListener` should remain a thin dispatcher.

It should:

* identify shop-managed inventory windows
* cancel the event
* fetch immutable session snapshot from router/store
* route to the correct menu handler
* clear session on quit

It should not:

* perform any async handoff itself
* touch world/container state directly
* own shared mutable state

### Specific rules

* keep event dispatch synchronous from the event callback
* menu handlers may call back into router/service layers, but those downstream layers must own the scheduling boundaries
* `onPlayerQuit` remains acceptable as the point to remove session state

### Acceptance criteria for `ShopMenuListener`

* listener remains thin and deterministic
* no shared mutable state race remains through session access
* no direct scheduler logic added here

---

## A.6 `ExchangeBuyServiceImpl` — exact Folia responsibilities

### Current issues

Current class:

* resolves player with `Bukkit.getPlayer(playerId)`
* validates/quotes purchase
* withdraws economy funds
* mutates player inventory with `player.getInventory().addItem(...)`
* logs transactions and updates stock

This bundles domain checks, economy calls, and player inventory mutation into one direct call path.

### Required post-refactor role

`ExchangeBuyServiceImpl` should become the **player-owned buy orchestrator**.

It should:

* run player/world-facing parts on the player’s entity scheduler
* keep planning and plugin-owned cache work separate from player inventory mutation
* hand off logging/persistence to async/plugin-owned services

It should not:

* assume it is always called from the correct thread context
* open inventories directly
* use region scheduler for player work

### Required internal shape

Split the current `buy(...)` flow into logical stages:

#### Stage 1 — player-owned validation snapshot

On player-owned execution:

* resolve player
* verify player online
* verify requested amount > 0
* resolve catalog entry
* compute quote
* verify inventory space / stackability constraints

Produce an immutable `PlannedBuy` result containing:

* item key
* requested amount
* approved amount
* item stack template
* quote
* any stock deduction intent

#### Stage 2 — player-owned commit

On player-owned execution:

* perform withdrawal
* add items to player inventory
* detect leftovers
* if leftovers occur, resolve rollback or partial-success policy explicitly

Important requirement:

* the plugin must define whether buy remains atomic or can become partial if inventory changed between planning and commit
* recommended v1 policy: re-check and fail atomically if the player cannot receive the full order at commit time

#### Stage 3 — plugin-owned stock/log update

After successful player commit:

* decrement stock if applicable
* enqueue transaction log write

These steps must not re-enter Bukkit player inventory mutation.

### Economy-call rule

Because the economy provider is an external dependency, keep economy calls in the same player-owned commit flow for now unless a specific provider-safe async model is later validated.

### Method contract after refactor

`buy(UUID playerId, ItemKey itemKey, int amount)` should either:

* remain synchronous externally but internally enforce player-owned execution, or
* return a future/result wrapper if a broader async API redesign is desired

For minimal drift, keep the external API synchronous but ensure callers already on player-owned execution do not double-hop unnecessarily.

### Acceptance criteria for `ExchangeBuyServiceImpl`

* no unsafe assumption about current thread ownership remains
* all player inventory mutation is executed as player-owned work
* logging and persistence are not tightly coupled to the inventory mutation path
* behavior on Paper remains unchanged from player perspective

---

## A.7 `ExchangeSellServiceImpl` — exact Folia responsibilities

### Current issues

Current class currently mixes three different ownership domains:

* player inventory selling (`sellHand`, `sellAll`)
* world container selling (`sellContainer` via looked-at block)
* held shulker selling (`sellContainer` fallback)

Those three paths must not continue to share one implicit execution model.

### Required post-refactor role

`ExchangeSellServiceImpl` should become a **coordinator**, not a monolithic mutation block.

It should:

* keep planning logic shared where safe
* split commit logic by ownership type
* route player-owned and region-owned operations through the correct executor paths
* preserve all-or-nothing behavior for mutation + payout where practical

It should not:

* resolve a world container and mutate it inline from an arbitrary caller context
* treat held shulkers and world containers as the same execution problem

### Required internal split

Introduce three explicit internal pathways:

#### Path 1 — `sellHand`

Ownership: **player/entity-owned**

Responsibilities:

* resolve player
* inspect main hand item
* validate sellability
* compute quote
* remove item from hand
* deposit funds
* restore original item on payout failure
* enqueue transaction log
* update stock cache

#### Path 2 — `sellAll`

Ownership: **player/entity-owned**

Responsibilities:

* resolve player
* scan player inventory
* build immutable sale plan
* remove planned items from player inventory
* deposit funds
* restore removed items on payout failure
* enqueue transaction log(s)
* update stock cache

#### Path 3 — `sellContainer`

Must split further into:

##### Path 3A — world block container

Ownership: **region/location-owned**

Responsibilities:

* on player-owned execution, resolve player and eye/target prerequisites if needed
* on region-owned execution for the target location:

  * validate targeted block still exists
  * validate supported container type
  * read container inventory
  * build immutable sale plan
  * remove planned items from container inventory
* then perform payout and rollback policy in a carefully staged way

Recommended v1 rule:

* keep payout + rollback within the same region-owned staged flow where possible to preserve atomicity
* avoid moving container state into async workflows

##### Path 3B — held shulker item

Ownership: **player/entity-owned**

Responsibilities:

* validate held item is a shulker box item with readable `BlockStateMeta`
* build immutable sale plan from shulker contents
* update shulker contents in meta
* replace main-hand item
* deposit funds
* restore original held item on payout failure
* enqueue logs and stock updates

### `sellContainer` front-door contract

The public `sellContainer(UUID playerId)` method should become a dispatcher:

1. run on player-owned execution
2. resolve whether the current action is:

   * looked-at supported world container, or
   * held shulker, or
   * neither
3. dispatch to the correct ownership-specific path

### Planning helpers that may remain shared

These helpers may stay common as long as they are pure inventory-read logic and do not mutate Bukkit state:

* sale planning
* skipped-item reasoning
* quote calculation
* line-result generation

### Helpers that must not stay generic/unsafe

These operations must stay ownership-specific:

* `inventory.setItem(...)`
* `player.getInventory().setItemInMainHand(...)`
* target block resolution and block state access
* payout rollback that restores Bukkit inventories

### Acceptance criteria for `ExchangeSellServiceImpl`

* `sellHand` and `sellAll` are explicitly player-owned
* world-container selling is explicitly region-owned
* held-shulker selling is explicitly player-owned
* no direct inline cross-domain assumptions remain
* rollback semantics remain correct
* player-visible behavior stays consistent

---

## A.8 GUI class spec

The menu classes should remain mostly UI/presentation classes, but their responsibilities need to be kept narrow.

### Shared rule for all GUI classes

These classes may:

* build `Inventory` contents
* read browse/detail data from services
* react to click slots
* call router/navigation methods

These classes should not:

* own shared mutable session state
* do async work directly
* touch world block/container state directly
* make scheduler decisions beyond optional delegation to router/executor

---

### A.8.1 `ExchangeRootMenu`

Current observed behavior:

* creates inventory with `Bukkit.createInventory(...)`
* opens it with `player.openInventory(...)`
* handles clicks and routes to category navigation

Required role after refactor:

* remain a pure player-menu view/controller

Responsibilities:

* build root inventory contents
* open inventory for the player
* decide which category was clicked
* delegate navigation to `ShopMenuRouter`

Non-responsibilities:

* no session storage
* no scheduler ownership logic beyond trusting router/player-owned call path
* no business mutation

Acceptance criteria:

* opening the root menu only occurs from player-owned execution
* click handling stays presentation-only

### A.8.2 `ExchangeSubcategoryMenu`

Current observed behavior:

* builds a subcategory inventory
* opens it for the player
* handles click routing into browse/back/close

Required role after refactor:

* remain presentation-only

Responsibilities:

* render visible subcategories
* delegate browse/back navigation to `ShopMenuRouter`

Non-responsibilities:

* no session ownership
* no scheduler selection
* no service-side mutation beyond browse reads

Acceptance criteria:

* no direct shared mutable state
* all open/close paths occur via player-owned flow

### A.8.3 `ExchangeBrowseMenu`

Current observed behavior:

* creates the browse inventory
* populates it from `exchangeService.browseCategory(...)`
* opens it for the player
* routes clicks to detail/back/next

Required role after refactor:

* remain a player-owned browse view/controller

Responsibilities:

* render page contents from browse results
* route item selection to detail navigation
* route back/next navigation through `ShopMenuRouter`

Important note:

* browse data is relatively safe because it is plugin-owned catalog/stock state, but inventory opening still remains player-owned

Acceptance criteria:

* no direct business mutation
* no shared mutable session ownership

### A.8.4 `ExchangeItemDetailMenu`

Current observed behavior:

* renders item detail inventory
* on buy-button click, calls `exchangeService.buy(...)`
* sends a player message
* reopens the detail screen on success

Required role after refactor:

* remain the item detail presentation/controller
* but stop assuming buy can be called as a raw inline business method without ownership guarantees

Responsibilities:

* render item detail view
* translate slot clicks into quantity selection
* invoke buy through a player-safe call path
* reopen/refresh the detail view only after buy result is returned on player-owned execution

Important design rule:

* this class may still call `exchangeService.buy(...)` directly if `buy(...)` itself now guarantees player-owned execution semantics
* otherwise, the buy invocation should be wrapped through router/executor infrastructure

Acceptance criteria:

* successful buys still refresh the detail view
* no unsafe inventory opening or message send occurs off player-owned execution

---

## A.9 `plugin.yml` Folia requirement

### Current issue

The current metadata does not declare Folia support.

### Required rule

Do **not** add:

* `folia-supported: true`

until all of the following are true:

* no direct Bukkit scheduler usage remains in active paths
* GUI session storage is safe
* player inventory mutations are explicitly player-owned
* world container mutations are explicitly region-owned
* manual Paper and Folia validation has been completed

### Final metadata action

After the above conditions are met, add:

```yaml
folia-supported: true
```

---

## A.10 Suggested implementation order by class

### Step 1

* add `PlatformMode`
* add `PlatformExecutor`
* add Paper/Folia executor implementations

### Step 2

* refactor `ServiceRegistry` to inject executor and stop using Bukkit scheduler directly

### Step 3

* add `MenuSessionStore`
* refactor `ShopMenuRouter` to use it

### Step 4

* make `ExchangeBuyServiceImpl` explicitly player-owned

### Step 5

* split `ExchangeSellServiceImpl` into player-owned and region-owned pathways
* add `ContainerTargetResolver`
* add planning helpers if needed

### Step 6

* validate GUI classes against the new execution boundaries

### Step 7

* Paper regression tests
* Folia validation tests
* only then mark `folia-supported: true`

---

## A.11 Minimal no-drift target

The minimal acceptable Folia remediation for this codebase is:

* no Bukkit scheduler use in `ServiceRegistry`
* `ShopMenuRouter` no longer uses `HashMap`
* all GUI opening paths remain player-owned
* `ExchangeBuyServiceImpl.buy(...)` explicitly runs as player-owned work
* `ExchangeSellServiceImpl.sellHand(...)` and `sellAll(...)` explicitly run as player-owned work
* `ExchangeSellServiceImpl.sellContainer(...)` explicitly dispatches to:

  * region-owned block-container logic
  * player-owned held-shulker logic
* plugin passes Paper and Folia smoke tests without ownership violations

That is the smallest coherent scope that would make the plugin honestly supportable on Folia.
