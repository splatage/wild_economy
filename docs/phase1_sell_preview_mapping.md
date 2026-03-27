# Phase 1 — Exact File-by-File Patch Plan (No Parallel Systems)

## Architectural rule

Phase 1 must be implemented as a **read-only projection of the existing sell system**.

That means:

- reuse `StockState`
- reuse `StockSnapshot`
- reuse `SellQuote`
- reuse `ExchangeSellServiceImpl` planning logic
- do **not** add parallel enums, pricing helpers, or preview planners

The only new feature surface is a preview command that reads from the existing sell domain.

---

## What the codebase already gives us

The attached repo already has the key pieces we need:

- `ExchangeSellServiceImpl`
  - existing grouped inventory/container sale planning
  - existing remove/restore/commit path
- `StockSnapshot`
  - includes `StockState`
- `StockStateResolver`
  - already defines the stock-state semantics
- `SellQuote`
  - already defines the quote model
- `ExchangeService`
  - existing façade used by commands
- `ShopCommand`
  - existing `/shop ...` subcommand router
- `plugin.yml`
  - current command registration surface

So the elegant Phase 1 edit set is **small and additive**:

1. formalize the existing internal sale plan in `ExchangeSellServiceImpl`
2. expose a read-only preview result from the same planner
3. wire `/shop sell preview`
4. wire `/worth` as an alias to the same preview path
5. clean up wording drift in grouped outputs
6. move `sellHand` onto the same planner spine where sensible

---

# Exact files to modify

## 1) `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellService.java`

### Why this file changes
The preview command must call the existing sell domain through an explicit service method, not by duplicating planner logic in the command layer.

### Add
A new read-only method:

```java
SellPreviewResult previewInventorySell(UUID playerId);
```

### Why this is the right place
This keeps preview in the sell domain, which is where it belongs.  
It avoids creating a second “preview-only” service that would compete with the real sell flow.

---

## 2) `src/main/java/com/splatage/wild_economy/exchange/domain/SellPreviewResult.java` **(new)**

### Why this file is needed
Preview needs a dedicated read-only result type. It should not be forced into `SellAllResult`, because `SellAllResult` is an execution result.

### Recommended shape

```java
public record SellPreviewResult(
    boolean success,
    List<SellPreviewLine> lines,
    BigDecimal totalQuoted,
    List<String> skippedDescriptions,
    String message
) {}
```

### Why this is elegant
This adds a **read-only projection type** without inventing a second quote system.

---

## 3) `src/main/java/com/splatage/wild_economy/exchange/domain/SellPreviewLine.java` **(new)**

### Why this file is needed
Preview lines are not the same thing as `SellLineResult`.

`SellLineResult` is a **completed sale** line.  
Preview needs a **quoted sale** line that still carries the live stock state.

### Recommended shape

```java
public record SellPreviewLine(
    ItemKey itemKey,
    String displayName,
    int amountQuoted,
    BigDecimal effectiveUnitPrice,
    BigDecimal totalQuoted,
    StockState stockState,
    boolean tapered
) {}
```

### Important rule
Use the existing `StockState`.  
Do **not** add a second enum such as `StockLevel`.

---

## 4) `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeSellServiceImpl.java`

### This is the main Phase 1 file.

### Current shape
This file already contains the real planner spine:

- `planSalesFromInventory(...)`
- `removePlannedItems(...)`
- `restorePlannedItems(...)`
- `completePlannedSales(...)`

### What should change

#### A. Add preview entrypoint
Add:

```java
@Override
public SellPreviewResult previewInventorySell(final UUID playerId)
```

Flow:
1. resolve online player
2. call the existing inventory planner
3. if empty, return failed preview result with skipped descriptions
4. map planned lines into `SellPreviewLine`
5. return total + skipped + message

No mutation. No payout. No stock writes. No transaction logging.

---

#### B. Reuse existing planner internals
Do **not** create a separate preview planner.

Instead, extend the internal plan model so each planned line carries the existing `StockSnapshot` or at least `StockState`.

Today the internal record is:

```java
private record GroupedPlannedSale(
    ItemKey itemKey,
    String displayName,
    int amount,
    SellQuote quote,
    List<InventoryRemoval> removals
) {}
```

Recommended change:

```java
private record GroupedPlannedSale(
    ItemKey itemKey,
    String displayName,
    int amount,
    SellQuote quote,
    StockSnapshot stockSnapshot,
    List<InventoryRemoval> removals
) {}
```

### Why
The planner already resolves stock before quoting.  
Keeping `StockSnapshot` on the planned line lets preview reuse that exact same stock interpretation without recalculating or introducing parallel state logic.

---

#### C. Rename internal records for clarity (recommended, not required)
Current names:
- `SalePlanning`
- `GroupedPlannedSale`
- `InventoryRemoval`

Recommended:
- `SalePlan`
- `PlannedSaleLine`
- `InventoryRemoval`

This is optional, but it makes the internal architecture clearer and better aligned with the design docs.

---

#### D. Add held-item planner
Current inconsistency:
- `sellAll` and `sellContainer` go through grouped planning
- `sellHand` does not

Add a helper:

```java
private SalePlanning planHeldItem(ItemStack held)
```

or, if renamed,

```java
private SalePlan planHeldItem(ItemStack held)
```

This helper should:
- validate the held item
- resolve catalog entry
- resolve current `StockSnapshot`
- quote via existing `PricingService`
- return a one-line plan

### Why
This removes the last major divergence in the sell domain.

---

#### E. Refactor `sellHand(...)` to use the planner
After the helper exists, change `sellHand(...)` to:

1. build held-item plan
2. fail if plan empty
3. remove held item
4. deposit payout
5. restore on failure
6. finalize through the same commit path used by grouped sells

This makes hand-selling and grouped selling conceptually the same system.

---

#### F. Add preview mapping helper
Recommended helper:

```java
private SellPreviewResult toPreviewResult(SalePlanning planning)
```

or equivalent.

This helper should:
- map each planned sale line to `SellPreviewLine`
- use `sale.stockSnapshot().stockState()`
- preserve `quote.tapered()`
- preserve grouped-by-item-type semantics

---

#### G. Leave these parts alone
Do **not** disturb:
- container protection logic
- lock checks
- Folia targeting flow
- held-shulker restore behavior
- payout-before-stock/log ordering

That behavior is already correct and should remain stable.

---

## 5) `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeService.java`

### Why this file changes
Commands currently talk to the façade, not directly to `ExchangeSellService`.

### Add
A new façade method:

```java
SellPreviewResult previewInventorySell(UUID playerId);
```

### Why
This keeps command wiring consistent with the existing service architecture.

---

## 6) `src/main/java/com/splatage/wild_economy/exchange/service/ExchangeServiceImpl.java`

### Why this file changes
The façade needs to delegate the new preview call.

### Add delegation
Forward the new method directly to `exchangeSellService.previewInventorySell(playerId)`.

### Why
This keeps preview as a thin façade pass-through, with no duplicate logic.

---

## 7) `src/main/java/com/splatage/wild_economy/command/ShopSellPreviewSubcommand.java` **(new)**

### Why this file is needed
This is the canonical new player-facing command.

### Responsibilities
Only:
- confirm sender is a player
- run through `PlatformExecutor`
- call `exchangeService.previewInventorySell(...)`
- format grouped preview output

### It must NOT:
- scan inventory directly
- quote directly
- resolve stock state directly

That would create the parallel system we explicitly want to avoid.

### Output requirements
Grouped by item type, not stacks.

Suggested output shape:

```text
Sell preview:
 - 128x Wheat for $45.00 [LOW]
 - 64x Carrot for $18.00 [HEALTHY]
 - 12x Iron Ingot for $30.00 [HIGH] (reduced)

Skipped:
 - Diamond Sword x1

Total quoted payout: $93.00
```

If truncated:

```text
 - ... and 8 more item type(s)
 - ... and 5 more skipped entries
```

### Presentation rule
Use the existing `StockState` names for now unless you later choose to add a formatter-only label mapping.

---

## 8) `src/main/java/com/splatage/wild_economy/command/ShopCommand.java`

### Why this file changes
`/shop sell preview` must route through the existing subcommand tree.

### Current limitation
`ShopCommand` only handles:
- `sellhand`
- `sellall`
- `sellcontainer`

### Required change
Add a preview subcommand dependency:

```java
private final ShopSellPreviewSubcommand sellPreviewSubcommand;
```

Update constructor accordingly.

Then extend routing to support:

- `/shop sell preview`

Since the current command style is flattened (`/shop sellall`, `/shop sellhand`, etc.), there are two possible approaches:

### Recommended approach: support both
Keep the current flat commands, but add nested parsing for preview:

- `/shop sell preview`

Implementation suggestion:
- if `args[0].equalsIgnoreCase("sell") && args.length >= 2 && args[1].equalsIgnoreCase("preview")`
  -> preview subcommand
- keep existing flat `sellhand`, `sellall`, `sellcontainer`

### Why this is the better compromise
It preserves backward compatibility while still giving you the cleaner canonical preview form.

---

## 9) `src/main/java/com/splatage/wild_economy/command/ShopSellAllSubcommand.java`

### Why this file changes
It currently calls grouped item-type results “stack(s)”.

### Required edits
Change:
- `more stack(s)` -> `more item type(s)`
- `more skipped stack(s)` -> `more skipped entries`

### Why
The planner groups by item type, not by raw inventory stack, so the current wording is inaccurate.

### Important note
No logic changes needed here beyond wording unless you later choose to extract a shared formatter.

---

## 10) `src/main/java/com/splatage/wild_economy/command/ShopSellContainerSubcommand.java`

### Why this file changes
Same wording issue as `ShopSellAllSubcommand`.

### Required edits
Change:
- `more stack(s)` -> `more item type(s)`
- `more skipped stack(s)` -> `more skipped entries`

### Why
Same reason: grouped output must describe grouped item types, not stacks.

---

## 11) `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`

### Why this file changes
It must instantiate and register the new preview command.

### Required edits

#### A. Construct preview subcommand
Add:

```java
final ShopSellPreviewSubcommand sellPreviewSubcommand =
    new ShopSellPreviewSubcommand(this.exchangeService, this.platformExecutor);
```

#### B. Pass preview subcommand into `ShopCommand`
Update constructor call accordingly.

#### C. Register `/worth`
Get the plugin command and set the same preview executor:

```java
final PluginCommand worth = this.plugin.getCommand("worth");
if (worth != null) {
    worth.setExecutor(sellPreviewSubcommand);
}
```

### What should NOT happen
Do not instantiate a new special preview service here.  
It should use the same `exchangeService` already in use.

---

## 12) `src/main/resources/plugin.yml`

### Why this file changes
We need to expose the new alias cleanly.

### Recommended edit
Add:

```yaml
  worth:
    description: Preview the current Exchange sell value of your inventory.
    usage: /worth
    permission: wild_economy.shop.sell
```

### Important design note
Do **not** add a separate `sellpreview` top-level command.

Why:
- the locked scope says `/sell preview` is canonical
- `/worth` is the familiar alias
- a third top-level command just adds surface clutter

### Also update `/shop` usage text
Current:

```yaml
usage: /shop [sellhand|sellall|sellcontainer]
```

Recommended:

```yaml
usage: /shop [sell preview|sellhand|sellall|sellcontainer]
```

and update description/help text accordingly.

---

# Files that should NOT change in Phase 1

These are intentionally out of scope unless compile wiring forces a minimal touch:

- `StockState.java`
- `StockSnapshot.java`
- `StockStateResolver.java`
- `SellQuote.java`
- `PricingServiceImpl.java`
- `StockServiceImpl.java`
- container protection integration classes
- GUI browse/view models
- transaction repositories
- economy ledger system

These already express the domain correctly and should be reused, not edited.

---

# Exact non-goals to enforce elegance

Do not add any of the following:

- `StockLevel`
- `PreviewStockState`
- `SellPreviewPlannerService`
- command-side inventory scanning logic
- command-side quoting logic
- duplicate stock threshold logic
- a second pricing path for previews

If a change introduces any of those, it is drifting into a competing system.

---

# Implementation order

## Step 1
Add:
- `SellPreviewResult`
- `SellPreviewLine`

## Step 2
Extend `ExchangeSellService` and `ExchangeService` with preview method

## Step 3
Refactor `ExchangeSellServiceImpl`
- add stock snapshot to planned lines
- add preview method
- add held-item planner
- unify `sellHand`

## Step 4
Add `ShopSellPreviewSubcommand`

## Step 5
Update `ShopCommand` routing

## Step 6
Update `ServiceRegistry` registration

## Step 7
Update `plugin.yml`

## Step 8
Fix grouped wording in sell-all and sell-container commands

---

# Definition of done

Phase 1 is complete when:

- `/shop sell preview` works
- `/worth` calls the exact same preview flow
- preview lines are grouped by item type
- preview lines reuse the existing `StockState`
- no new preview-only planner or stock enum exists
- `sellHand` uses the same planner spine as the grouped sell flows
- grouped output no longer says “stack(s)” when it means grouped item types

---

# Final judgment

The elegant implementation is **not** to bolt a preview feature beside the sell system.

It is to make preview a **read-only lens on the sell system that already exists**.

That gives you:
- one planner
- one quote model
- one stock-state model
- one source of truth
- minimal drift
