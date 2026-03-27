# wild_economy — Phase 1 Mapping and Implementation Document

## Scope of Phase 1

Phase 1 for this codebase is **not** to add sell commands from scratch. Those already exist. The real Phase 1 work is to introduce a **shared sale-planning / preview path** and make the current hand, inventory, and container sell flows consume it consistently.

In practical terms, Phase 1 is:

- extract the existing grouped sale planning into a reusable first-class planning layer
- make `/sellhand`, `/sellall`, and `/sellcontainer` all build from that same plan model
- separate **plan / preview / execute** cleanly
- standardize output formatting so the sell surface no longer feels stack-oriented or command-specific

---

## What already exists

### Command surface

The command layer is already present and thin:

- `src/main/java/com/splatage/wild_economy/command/ShopSellHandSubcommand.java`
- `src/main/java/com/splatage/wild_economy/command/ShopSellAllSubcommand.java`
- `src/main/java/com/splatage/wild_economy/command/ShopSellContainerSubcommand.java`
- `src/main/java/com/splatage/wild_economy/command/ShopCommand.java`

These subcommands currently invoke the exchange layer directly and then format messages per command.

### Service surface

The public sell API already exists on:

- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeService.java`
- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeServiceImpl.java`

Current public methods:

- `sellHand(UUID playerId)`
- `sellAll(UUID playerId)`
- `sellContainer(UUID playerId)`

### Execution engine

Most of the real sell logic already lives in:

- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellServiceImpl.java`

This class already contains the embryo of the future Phase 1 model:

- grouped inventory scanning
n- grouped pricing
- planned removals
- restore-on-payout-failure
- grouped sell line results

The key existing internal planning method is:

- `planSalesFromInventory(...)`

And the existing plan shape is internal/private:

- `SalePlanning`
- `GroupedPlannedSale`
- `InventoryRemoval`
- `GroupedSaleAccumulator`

### Folia-sensitive container path

Container execution already has a coordinator:

- `src/main/java/com/splatage/wild_economy/exchange/service/FoliaContainerSellCoordinator.java`

This exists because placed-container selling has region/thread ownership constraints.

### Wiring

Construction is already centralized in:

- `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

That means Phase 1 can be added cleanly without scattering creation logic.

---

## Current architecture assessment

The code already has a usable execution path, but it is still **execution-first** rather than **plan-first**.

### What is good already

- `/sellall` and `/sellcontainer` already group by item type before pricing
- payout failure already restores inventory/container contents
- transaction logging and stock updates happen after successful payout
- command classes are thin and mostly defer to services
- container selling already respects the Folia placement/ownership model

### What is still missing for Phase 1

- no reusable public/intermediate **sale plan** model
- no clean preview API that can be called without executing the sale
- `sellHand(...)` still bypasses the grouped planning path and performs its own one-off flow
- command output formatting is duplicated and slightly inconsistent
- “preview” is implicit inside execution, not a first-class step

So the Phase 1 refactor should not redesign the economy. It should promote the existing internal planning logic into a proper shared layer.

---

## Phase 1 target architecture

## 1. Introduce a first-class sale planning layer

Create a dedicated planner responsible only for **inspection and quoting**, not mutation.

### Recommended new service

`src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellPlanner.java`

Suggested responsibility:

- validate items for selling
- group by `ItemKey`
- quote using `PricingService`
- collect skipped reasons
- build a reusable plan object
- perform **no inventory mutation**
- perform **no stock writes**
- perform **no transaction logging**
- perform **no economy deposit**

### Recommended implementation

`src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellPlannerImpl.java`

This should absorb the logic currently embedded in `ExchangeSellServiceImpl.planSalesFromInventory(...)` and the one-off sell-hand quote path.

---

## 2. Promote the current private plan records into shared domain objects

Right now the planning records are private implementation details inside `ExchangeSellServiceImpl`. That is the main reason preview cannot be exposed cleanly.

### Recommended new domain types

Under:

`src/main/java/com/splatage/wild_economy/exchange/domain/`

Suggested types:

#### `SellPlan`
Represents one complete sell attempt before execution.

Suggested fields:

- `List<PlannedSellLine> plannedLines`
- `List<String> skippedDescriptions`
- `BigDecimal totalEarned`
- `boolean taperedAny`
- `SellSourceType sourceType`
- `String sourceDescription`

#### `PlannedSellLine`
Represents one grouped item type to be sold.

Suggested fields:

- `ItemKey itemKey`
- `String displayName`
- `int amount`
- `BigDecimal effectiveUnitPrice`
- `BigDecimal totalEarned`
- `boolean tapered`
- `List<InventoryRemoval> removals`

#### `InventoryRemoval`
Can remain effectively the same as today, but should move out of the private record scope if execution remains two-stage.

Suggested fields:

- `int slot`
- `ItemStack originalStack`

#### `SellSourceType`
Keeps output and execution semantics explicit.

Suggested values:

- `HAND`
- `PLAYER_INVENTORY`
- `CONTAINER`
- `HELD_SHULKER`

This lets the executor and formatter know what they are dealing with without command-specific branching.

---

## 3. Split execution away from planning

Once the plan exists, `ExchangeSellServiceImpl` should become an executor/orchestrator rather than the place where planning logic lives.

### Recommended role for `ExchangeSellServiceImpl` after refactor

Keep it as the mutation path:

- resolve player / container target
- request a `SellPlan` from the planner
- short-circuit on empty plans
- remove planned items
- deposit payout
- restore on failure
- update stock
- log transactions
- return result DTOs

That preserves the current safety properties, but with a much cleaner internal shape.

### Internal structure after refactor

`ExchangeSellServiceImpl` should roughly have these responsibilities:

#### Source resolution
- find player
- find held item
- find inventory
- resolve supported placed container or held shulker

#### Execution
- `executePlanAgainstInventory(...)`
- `executePlanAgainstHeldShulker(...)`
- `completePlannedSales(...)`

#### Formatting/result projection
- map `SellPlan` → `SellHandResult`
- map `SellPlan` → `SellAllResult`
- map `SellPlan` → `SellContainerResult`

That is much clearer than mixing scanning, quoting, removal, payout, and string formatting in one flow.

---

## 4. Unify hand selling onto the same plan model

This is the most important Phase 1 behavioural cleanup.

### Current state

`sellHand(UUID playerId)` in `ExchangeSellServiceImpl` currently does its own separate path:

- validate held item
- fetch catalog entry
- quote single stack
- remove item directly
- deposit
- restore on failure
- add stock
- log sale
- return a dedicated `SellHandResult`

### Why this is a problem

- it duplicates logic already present in grouped planning
- preview cannot be shared across all sell entry points while hand selling remains special-cased
- future sell formatting or stock-pressure hints would need to be implemented twice

### Phase 1 change

Hand selling should become:

- build `SellPlan` from the main-hand slot only
- execute that plan using the same execution machinery as any other source
- project the single planned line back into `SellHandResult`

This preserves the external command behaviour, but collapses the internal drift.

---

## 5. Keep sell result DTOs for compatibility, but generate them from the plan

The current public result records are already reasonable and should probably stay for Phase 1:

- `SellHandResult`
- `SellAllResult`
- `SellContainerResult`
- `SellLineResult`

The refactor should **not** immediately replace these externally. Instead:

- build one `SellPlan`
- execute it
- map it into the existing result records

This avoids breaking command and GUI callers while still improving the core design.

---

## 6. Standardize sell message formatting

At the moment:

- `ShopSellAllSubcommand` formats sold lines and skipped lines inline
- `ShopSellContainerSubcommand` duplicates similar formatting
- `ShopSellHandSubcommand` only sends the service message

That means the command layer is doing presentation work in multiple places.

### Recommended new formatter

`src/main/java/com/splatage/wild_economy/command/SellCommandSummaryFormatter.java`

Suggested responsibilities:

- format sold lines consistently
- format skipped lines consistently
- clamp output count consistently
- use “item type(s)” rather than “stack(s)” where lines are grouped

### Why this matters

This is not cosmetic only. Right now `/sellall` and `/sellcontainer` are already grouping by item type, but the overflow message still says “more stack(s)”. That mismatches the real behaviour and weakens player understanding.

A formatter lets Phase 1 fix that cleanly in one place.

---

## Exact file mapping for Phase 1

## Existing files to modify

### `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellServiceImpl.java`

Primary Phase 1 refactor target.

Changes:

- extract planning logic into a planner service
- refactor `sellHand(...)` to use shared planning
- keep execution and recovery here
- stop owning private planning records if they are promoted

### `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeService.java`

Optional change only if you want preview APIs to be public in Phase 1.

Possible additions:

- `SellPlan previewSellHand(UUID playerId)`
- `SellPlan previewSellAll(UUID playerId)`
- `SellPlan previewSellContainer(UUID playerId)`

If you want to keep preview internal for now, leave this interface untouched in Phase 1.

### `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeServiceImpl.java`

Only needs changes if preview APIs are added publicly.

### `src/main/java/com/splatage/wild_economy/command/ShopSellHandSubcommand.java`

Minor change if you introduce a shared formatter or confirm/preview behaviour.

### `src/main/java/com/splatage/wild_economy/command/ShopSellAllSubcommand.java`

Refactor to delegate result display to a shared formatter.

### `src/main/java/com/splatage/wild_economy/command/ShopSellContainerSubcommand.java`

Refactor to delegate result display to a shared formatter.

### `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

Register:

- planner service
- optional formatter if injected
- any new public preview-capable service wiring

---

## Recommended new files

### Domain

- `src/main/java/com/splatage/wild_economy/exchange/domain/SellPlan.java`
- `src/main/java/com/splatage/wild_economy/exchange/domain/PlannedSellLine.java`
- `src/main/java/com/splatage/wild_economy/exchange/domain/InventoryRemoval.java`
- `src/main/java/com/splatage/wild_economy/exchange/domain/SellSourceType.java`

### Service

- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellPlanner.java`
- `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellPlannerImpl.java`

### Command formatting

- `src/main/java/com/splatage/wild_economy/command/SellCommandSummaryFormatter.java`

---

## Keep out of Phase 1

To avoid drift, do **not** mix these into Phase 1:

- transaction-history commands
- supplier stats
- stock-pressure GUI signals
- virtual category views
- price model redesign
- DB schema work
- confirmation UX state machines unless explicitly wanted now

Phase 1 should stay tightly focused on one thing:

> make sale planning a first-class shared layer and make all sell commands build from it.

---

## Behavioural contract for Phase 1

After the refactor, all three sell entry points should obey the same internal contract.

### Preview stage

- inspect source
- validate sellable items
- group by `ItemKey`
- quote against current stock snapshot
- collect skipped items/reasons
- compute total
- mark whether any line is tapered

### Execute stage

- remove only the planned items
- deposit the planned total
- restore items if payout fails
- apply stock deltas only after successful payout
- append transaction log entries only after successful payout

### Result stage

- project the executed plan into existing result DTOs
- format player-facing output consistently

That gives you a stable foundation for later preview commands, GUI confirmation, stock-pressure hints, and supplier tracking.

---

## Risk notes

### 1. Folia container ownership

Do not collapse `FoliaContainerSellCoordinator` into the planner. The planner should stay pure and source-agnostic. Container thread/region choreography should remain in the coordinator/executor layer.

### 2. ItemStack retention in `InventoryRemoval`

If `InventoryRemoval` becomes a shared domain object, be careful that it is still treated as an execution detail and not cached or persisted. It contains live cloned Bukkit item data.

### 3. Public preview API timing

You do not need to expose preview methods publicly in Phase 1 to get the architectural benefit. A purely internal plan layer is enough. Public preview commands can come later.

### 4. Message drift

The wording should reflect grouped item-type selling, not stack-by-stack selling. This is a small but important player-trust detail.

---

## Recommended implementation order

### Step 1
Create shared domain records for the sell plan.

### Step 2
Extract `planSalesFromInventory(...)` logic into `ExchangeSellPlannerImpl`.

### Step 3
Add a single-slot / hand planning path to the planner.

### Step 4
Refactor `ExchangeSellServiceImpl` to execute a `SellPlan` instead of building plans inline.

### Step 5
Move duplicated command summary formatting into one formatter.

### Step 6
Regression-check:

- `/sellhand`
- `/sellall`
- `/sellcontainer` on placed chest/barrel/shulker
- `/sellcontainer` on held shulker
- payout failure restore paths
- shulker protection in `/sellall`
- tapered/reduced value messaging

---

## Final recommendation

For this repository, Phase 1 should be treated as an **internal architecture cleanup that unlocks later UX features**, not as a player-facing feature drop by itself.

That is the right move because the code already contains the core behaviour; what it lacks is a durable shared planning model.

Once this is in place, later Tier 1 work becomes much easier:

- stock-aware preview hints
- unified sell preview output
- confirmation flows
- supplier tracking hooks
- cleaner transaction/history exposure

Without this refactor, each of those features will keep re-implementing sell logic in parallel.
