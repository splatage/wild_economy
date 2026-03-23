# wild_economy — Authoritative Pricing Runtime Engineering Spec

Status: Canonical source of truth for the pricing/runtime simplification scope discussed in this chat  
Authority: This document supersedes earlier ad hoc notes for the pricing schema and runtime catalog lifecycle  
Repo basis reviewed: uploaded `wild_economy-main.zip` and commit line around `91be9669b433c4b4457fcdf73aa2ce89e7b1b941`  
Audience: maintainers and implementers of `wild_economy`

---

## 1. Purpose

This document defines the authoritative target architecture for the `wild_economy` pricing system, with emphasis on:

- pricing schema
- runtime catalog lifecycle
- admin generation/publish workflow
- command behavior
- config file contracts
- runtime API contracts
- file/class responsibilities
- migration/removal boundaries

This spec exists to eliminate pricing drift caused by overlapping catalog pipelines and mixed schema roles.

---

## 2. Problem Statement

The reviewed codebase currently mixes multiple partially overlapping pricing/catalog models:

1. an older generated-catalog import path,
2. a newer `/shopadmin` build/apply path,
3. a reference-based config model using root values, eco envelopes, and stock profiles,
4. a flattened runtime model using direct buy/sell/stock values.

This overlap has produced schema drift at the seam between admin generation, published config, runtime loading, and live pricing.

The goal of this spec is to collapse that ambiguity into one clear model.

---

## 3. Locked Decisions

The following decisions are locked for this scope.

### 3.1 Single runtime catalog file

`exchange-items.yml` is the **only** pricing/catalog file consumed by the runtime `/shop` path.

Runtime `/shop` must not depend on:

- `root-values.yml`
- `eco-envelopes.yml`
- `stock-profiles.yml`
- `generated/generated-catalog.yml`
- any other generated intermediate catalog file

Those files become **admin/build inputs only**, not runtime dependencies.

### 3.2 Admin resolves all reusable abstractions

Admin functions consume root values, rule/config inputs, recipe/Bukkit relationships, eco definitions, and stock definitions, then fully resolve each item into a runtime-ready published entry.

The admin pipeline may continue to use reusable references internally, but runtime must not.

### 3.3 Fully cacheable runtime catalog

Because `exchange-items.yml` is fully resolved, runtime must parse it once at startup/reload and build an immutable in-memory catalog.

Runtime pricing must operate from:

- cached item entry
- current stock snapshot

and nothing else.

### 3.4 Price bands are legacy and removed

Legacy `sell-price-bands` are removed from the target model.

They are superseded by a simpler per-item **eco envelope** contract that provides all data needed for dynamic price calculation.

### 3.5 Dynamic pricing remains runtime behavior

Prices are not precomputed for all stock states.

Admin publishes the resolved **pricing parameters** per item. Runtime computes live buy/sell quotes from those parameters and current stock.

---

## 4. Canonical Lifecycle

## 4.1 Admin/build lifecycle

Inputs:

- `root-values.yml`
- `policy-rules.yml`
- `policy-profiles.yml`
- `manual-overrides.yml`
- `stock-profiles.yml`
- `eco-envelopes.yml`
- Bukkit material universe
- Bukkit recipe graph / fallbacks

Build flow:

1. scan materials
2. derive rooted values
3. classify category
4. determine policy/profile
5. resolve stock profile
6. resolve eco envelope
7. compute resolved per-item runtime pricing parameters
8. stage generated reports/artifacts for review
9. on apply, publish the live runtime catalog to `exchange-items.yml`

Outputs:

- generated reports under `generated/` for admin review
- published live runtime catalog at `exchange-items.yml`

## 4.2 Runtime lifecycle

Startup/reload flow:

1. load `exchange-items.yml`
2. validate runtime schema
3. build immutable `ExchangeCatalog`
4. initialize stock service/cache
5. initialize pricing service
6. `/shop` and related player flows read the cached catalog only

Runtime quote flow:

1. lookup cached entry by `ItemKey`
2. lookup current `StockSnapshot`
3. compute buy/sell quote from entry eco parameters and stock
4. return quote

---

## 5. Authoritative File Roles

## 5.1 Runtime-consumed file

### `src/main/resources/exchange-items.yml` / live data folder `exchange-items.yml`

Role:
- published runtime catalog
- only catalog/pricing file consumed by `/shop` runtime

Characteristics:
- fully resolved
- no named stock-profile references
- no named eco-envelope references
- no unresolved root-value dependency
- no legacy sell-price-bands

## 5.2 Admin/build-only files

These files remain valid and important, but become admin/build inputs only.

- `root-values.yml`
- `policy-rules.yml`
- `policy-profiles.yml`
- `manual-overrides.yml`
- `stock-profiles.yml`
- `eco-envelopes.yml`

They must not be read by runtime pricing/catalog services after publish.

## 5.3 Generated review/report files

The following remain admin review artifacts only:

- `generated/generated-summary.yml`
- `generated/generated-diff.yml`
- `generated/generated-validation.yml`
- `generated/item-decision-traces.yml`
- `generated/generated-rule-impacts.yml`
- `generated/generated-review-buckets.yml`
- any successor generated report files

`generated/generated-catalog.yml` is **not** a runtime contract in the target design.
It may remain as a report/debug artifact or be removed, but runtime must not depend on it.

---

## 6. Canonical Runtime Schema — `exchange-items.yml`

This section defines the target published runtime schema.

```yaml
items:
  minecraft:wheat:
    display-name: "Wheat"
    category: FARMING_AND_FOOD
    generated-category: FARMING
    policy: PLAYER_STOCKED
    buy-enabled: true
    sell-enabled: true
    stock-cap: 10000
    turnover-amount-per-interval: 64
    base-worth: 4.00

    eco:
      min-stock-inclusive: 1000
      max-stock-inclusive: 10000

      buy-price-low-stock: 6.00
      buy-price-high-stock: 4.20

      sell-price-low-stock: 4.00
      sell-price-high-stock: 1.20
```

## 6.1 Required top-level item fields

For each `items.<itemKey>` entry, the following fields are required unless otherwise stated.

- `display-name: String`
- `category: ItemCategory`
- `generated-category: GeneratedItemCategory | optional but recommended`
- `policy: ItemPolicyMode`
- `buy-enabled: boolean`
- `sell-enabled: boolean`
- `stock-cap: long`
- `turnover-amount-per-interval: long`
- `base-worth: decimal`
- `eco: object`

## 6.2 Required eco fields

- `min-stock-inclusive: long`
- `max-stock-inclusive: long`
- `buy-price-low-stock: decimal`
- `buy-price-high-stock: decimal`
- `sell-price-low-stock: decimal`
- `sell-price-high-stock: decimal`

## 6.3 Eco semantics

The runtime eco object is already resolved per item.

- `low-stock` means price anchor applied at or below `min-stock-inclusive`
- `high-stock` means price anchor applied at or above `max-stock-inclusive`
- between the two anchors, pricing is linear

The admin pipeline is responsible for deriving these concrete values from reusable source definitions.

## 6.4 Validation rules

- `itemKey` must be canonicalized namespaced key
- `stock-cap >= 0`
- `turnover-amount-per-interval >= 0`
- `base-worth >= 0`
- `min-stock-inclusive >= 0`
- `max-stock-inclusive >= min-stock-inclusive`
- all eco prices must be non-negative
- `buy-price-low-stock >= buy-price-high-stock` unless a deliberate future exception is defined
- `sell-price-low-stock >= sell-price-high-stock` unless a deliberate future exception is defined

## 6.5 Removed/forbidden runtime fields

The following fields are legacy or admin-only and must not be part of the canonical runtime contract:

- `stock-profile`
- `eco-envelope`
- `policy-profile`
- `admin-policy`
- `runtime-policy`
- `stock-backed`
- `unlimited-buy`
- `requires-player-stock-to-buy`
- `sell-price-bands`

If such fields are present, runtime must ignore them or validation should reject them once migration is complete.

---

## 7. Canonical Price Calculation Contract

## 7.1 Inputs

Runtime quote calculation may use only:

- cached `ExchangeCatalogEntry`
- `StockSnapshot`
- requested amount

## 7.2 Buy quote contract

`quoteBuy(itemKey, amount, snapshot)` must compute unit and total buy price from:

- the item eco buy anchors
- current stock count
- linear interpolation between stock anchors

Target behavior:

- if `stock <= min-stock-inclusive`, use `buy-price-low-stock`
- if `stock >= max-stock-inclusive`, use `buy-price-high-stock`
- otherwise interpolate linearly

Then total price is unit price × amount, rounded once at money scale.

## 7.3 Sell quote contract

`quoteSell(itemKey, amount, snapshot)` must compute unit and total sell price from:

- the item eco sell anchors
- current stock count
- linear interpolation between stock anchors

Target behavior:

- if `stock <= min-stock-inclusive`, use `sell-price-low-stock`
- if `stock >= max-stock-inclusive`, use `sell-price-high-stock`
- otherwise interpolate linearly

For sell batches that cross boundaries, runtime may continue to use piecewise aggregation across the stock interval. That behavior is allowed and preferred for correctness.

## 7.4 Stock coupling

Dynamic pricing is driven by live stock.

Therefore:
- pricing service must never mutate stock
- stock service remains the authoritative source of stock state
- exchange services coordinate stock mutation and quote usage

---

## 8. Runtime Domain Model Contract

The runtime domain model must be simplified around the published catalog.

## 8.1 `ExchangeItemsConfig.RawItemEntry`

Target responsibility:
- mirror the published runtime YAML shape
- no unresolved admin/build references

Target fields:

- `ItemKey itemKey`
- `String displayName`
- `ItemCategory category`
- `GeneratedItemCategory generatedCategory`
- `ItemPolicyMode policyMode`
- `boolean buyEnabled`
- `boolean sellEnabled`
- `long stockCap`
- `long turnoverAmountPerInterval`
- `BigDecimal baseWorth`
- `ResolvedEcoRuntime eco`

`ecoEnvelopeKey` is removed from the runtime contract.

## 8.2 New runtime eco value object

Introduce a dedicated runtime value object, for example:

- `ResolvedEcoRuntime`

Suggested fields:

- `long minStockInclusive`
- `long maxStockInclusive`
- `BigDecimal buyPriceLowStock`
- `BigDecimal buyPriceHighStock`
- `BigDecimal sellPriceLowStock`
- `BigDecimal sellPriceHighStock`

This object becomes the single pricing parameter contract used by runtime.

## 8.3 `ExchangeCatalogEntry`

Target fields mirror runtime config, not admin inputs.

Suggested target fields:

- `ItemKey itemKey`
- `String displayName`
- `ItemCategory category`
- `GeneratedItemCategory generatedCategory`
- `ItemPolicyMode policyMode`
- `BigDecimal baseWorth`
- `long stockCap`
- `long turnoverAmountPerInterval`
- `boolean buyEnabled`
- `boolean sellEnabled`
- `ResolvedEcoRuntime eco`

Legacy fields that should be removed from the runtime catalog entry once migration is complete:

- `buyPrice` as a standalone static field if buy becomes fully dynamic
- `sellPrice` as a standalone static field if sell becomes fully dynamic
- `SellPriceBand sellEnvelope`

If transitional compatibility is needed, these may temporarily remain but must not be the long-term contract.

---

## 9. File/Class Responsibility Map

This section defines the target responsibility of key files and classes.

## 9.1 Keep in runtime path

### `src/main/java/com/splatage/wild_economy/config/ConfigLoader.java`
Role:
- load runtime `exchange-items.yml`
- validate/parse the published runtime schema

Must not be responsible for resolving admin-only references at runtime.

### `src/main/java/com/splatage/wild_economy/config/ExchangeItemsConfig.java`
Role:
- runtime config DTO for published `exchange-items.yml`

### `src/main/java/com/splatage/wild_economy/config/ConfigValidator.java`
Role:
- validate published runtime schema only
- reject malformed runtime entries

### `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogLoader.java`
Role:
- build immutable runtime catalog from `ExchangeItemsConfig`

After migration, it should not import generated catalogs or root values.

### `src/main/java/com/splatage/wild_economy/exchange/catalog/ExchangeCatalog.java`
Role:
- immutable cached runtime catalog

### `src/main/java/com/splatage/wild_economy/exchange/catalog/ExchangeCatalogEntry.java`
Role:
- fully resolved runtime item entry

### `src/main/java/com/splatage/wild_economy/exchange/pricing/PricingService.java`
Role:
- quote API contract

### `src/main/java/com/splatage/wild_economy/exchange/pricing/PricingServiceImpl.java`
Role:
- compute dynamic buy/sell quotes from cached item eco data and stock snapshot

### `src/main/java/com/splatage/wild_economy/exchange/stock/StockService.java`
Role:
- authoritative stock state API

### `src/main/java/com/splatage/wild_economy/exchange/stock/StockServiceImpl.java`
Role:
- authoritative in-memory stock cache + persistence path

### `src/main/java/com/splatage/wild_economy/bootstrap/PluginBootstrap.java`
Role:
- bootstrap/reload orchestration

### `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`
Role:
- compose runtime services around the published runtime catalog

## 9.2 Keep as admin/build-only path

### `src/main/java/com/splatage/wild_economy/catalog/admin/AdminCatalogActionService.java`
Role:
- command-facing admin orchestration

### `src/main/java/com/splatage/wild_economy/catalog/admin/AdminCatalogPhaseOneService.java`
Role:
- build/apply pipeline
- resolve root values, stock profiles, eco envelopes, rules, and overrides
- publish final `exchange-items.yml`

### `src/main/java/com/splatage/wild_economy/catalog/rootvalue/*`
Role:
- admin-only rooted value derivation inputs

### `src/main/java/com/splatage/wild_economy/catalog/derive/*`
Role:
- admin-only item value derivation

### `src/main/java/com/splatage/wild_economy/catalog/recipe/*`
Role:
- admin-only recipe graph building and derivation support

### `src/main/java/com/splatage/wild_economy/catalog/classify/*`
Role:
- admin-only category classification

## 9.3 Remove from runtime path

### `src/main/java/com/splatage/wild_economy/exchange/catalog/GeneratedCatalogImporter.java`
Target status:
- removed from runtime path
- may be deleted entirely if no longer useful as a report reader

### `src/main/java/com/splatage/wild_economy/exchange/catalog/RootValueImporter.java`
Target status:
- removed from runtime path
- root values become admin-only inputs

### `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogMergeService.java`
Target status:
- either removed or reduced to a trivial runtime mapper
- must no longer perform admin-style resolution from root values / named eco envelopes / stock profiles

### `src/main/java/com/splatage/wild_economy/config/EcoEnvelopesConfig.java`
Target status:
- admin/build-only
- not a runtime dependency

### `src/main/java/com/splatage/wild_economy/config/StockProfilesConfig.java`
Target status:
- admin/build-only
- not a runtime dependency

---

## 10. Command Contract

The player-facing command surface remains stable unless explicitly changed elsewhere.

## 10.1 Player commands

From `src/main/resources/plugin.yml`:

- `/shop`
- `/sellhand`
- `/sellall`
- `/sellcontainer`

These commands must use only the cached runtime `ExchangeCatalog` plus live stock/economy services.

## 10.2 Admin commands

Current admin surface:

- `/shopadmin`
- `/shopadmin reload`
- `/shopadmin gui`
- `/shopadmin generatecatalog`
- `/shopadmin catalog preview`
- `/shopadmin catalog validate`
- `/shopadmin catalog diff`
- `/shopadmin catalog apply`
- `/shopadmin item <item_key>`

Target contract:

- `preview`, `validate`, `diff`, and `item` operate on admin/build state and generated review artifacts
- `apply` publishes the final live `exchange-items.yml`
- `reload` rebuilds runtime services from the published runtime catalog

Implementation note:
`generatecatalog` may remain as an alias for preview/build, but the canonical workflow should center on `catalog preview|validate|diff|apply`.

---

## 11. Runtime API Contracts

## 11.1 Pricing service

Current interface:

```java
public interface PricingService {
    BuyQuote quoteBuy(ItemKey itemKey, int amount, StockSnapshot stockSnapshot);
    SellQuote quoteSell(ItemKey itemKey, int amount, StockSnapshot stockSnapshot);
}
```

Authoritative contract:

- must be pure with respect to stock mutation
- must throw or fail deterministically for missing catalog entries
- must consume only cached runtime item data and stock snapshot
- must not perform file/config/profile lookups

## 11.2 Stock service

Current interface:

```java
public interface StockService {
    StockSnapshot getSnapshot(ItemKey itemKey);
    long getAvailableRoom(ItemKey itemKey);
    void addStock(ItemKey itemKey, int amount);
    boolean tryConsume(ItemKey itemKey, int amount);
    int consumeUpTo(ItemKey itemKey, int amount);
    void removeStock(ItemKey itemKey, int amount);
    void flushDirtyNow();
    StockMetricsSnapshot metricsSnapshot();
    void shutdown();
}
```

Authoritative contract:

- remains the authoritative stock API
- remains separate from pricing
- runtime quote logic depends on `StockSnapshot` only

## 11.3 Admin publish contract

Introduce or formalize an internal publish contract equivalent to:

- build proposed plan entries
- validate them
- diff against current live catalog
- publish fully resolved runtime catalog to `exchange-items.yml`

This contract must be atomic from runtime’s perspective:

- write new file
- reload runtime
- swap catalog cache as one operation

---

## 12. Migration Rules

## 12.1 What must change

1. runtime loader must stop depending on generated catalog and root values
2. runtime config model must stop carrying unresolved admin references
3. published `exchange-items.yml` must contain resolved eco parameters
4. price bands must be removed from runtime schema
5. pricing service must move to eco-envelope-driven dynamic buy and sell calculations

## 12.2 Transitional compatibility

Temporary compatibility is allowed only when necessary to land the migration safely.

Examples:
- old fields may be read during one migration window
- old runtime fields may remain on records temporarily

But transitional compatibility must not become the new source of drift.

## 12.3 End-state cleanup

When migration is complete:

- remove runtime use of `GeneratedCatalogImporter`
- remove runtime use of `RootValueImporter`
- remove runtime use of `EcoEnvelopesConfig`
- remove runtime use of `StockProfilesConfig`
- remove legacy sell band models from runtime

---

## 13. Acceptance Criteria

The implementation is complete only when all of the following are true.

### 13.1 Runtime source of truth

- runtime `/shop` consumes only `exchange-items.yml`
- runtime pricing/catalog path has no dependency on admin source files

### 13.2 Cache behavior

- `exchange-items.yml` is parsed once per startup/reload
- runtime uses immutable cached catalog entries
- quotes require only cached item entry + stock snapshot

### 13.3 Pricing behavior

- buy pricing is dynamic from the resolved eco contract
- sell pricing is dynamic from the resolved eco contract
- no runtime use of legacy sell-price-bands

### 13.4 Publish behavior

- `/shopadmin catalog apply` publishes a complete runtime-ready `exchange-items.yml`
- published file contains all eco data needed for runtime calculation
- runtime reload after apply is deterministic

### 13.5 Drift elimination

- no duplicate runtime catalog schemas
- no different meanings of `exchange-items.yml` in runtime vs published state
- no generated catalog file required for runtime operation

---

## 14. Non-Goals for This Scope

The following are explicitly outside this spec unless separately approved.

- redesign of broader shop GUI behavior
- redesign of stock persistence backend
- redesign of permission model
- unrelated command changes
- unrelated economy mechanics outside the pricing/runtime catalog seam

---

## 15. Implementation Order

Recommended implementation order:

1. lock runtime `exchange-items.yml` schema
2. introduce runtime `ResolvedEcoRuntime` model
3. update admin publish path to emit the new runtime schema
4. update `ConfigLoader` / `ExchangeItemsConfig` / `ConfigValidator` to read the new schema
5. simplify `CatalogLoader` to build catalog only from runtime file
6. update `ExchangeCatalogEntry` and `PricingServiceImpl` to use runtime eco contract
7. remove runtime dependence on generated catalog/root value/profile loaders
8. remove legacy sell-price-bands from code and data
9. add tests for publish -> reload -> quote lifecycle

---

## 16. Source Files of Immediate Interest

These are the primary files for implementing this spec.

### Runtime
- `src/main/java/com/splatage/wild_economy/config/ConfigLoader.java`
- `src/main/java/com/splatage/wild_economy/config/ExchangeItemsConfig.java`
- `src/main/java/com/splatage/wild_economy/config/ConfigValidator.java`
- `src/main/java/com/splatage/wild_economy/exchange/catalog/CatalogLoader.java`
- `src/main/java/com/splatage/wild_economy/exchange/catalog/ExchangeCatalogEntry.java`
- `src/main/java/com/splatage/wild_economy/exchange/pricing/PricingService.java`
- `src/main/java/com/splatage/wild_economy/exchange/pricing/PricingServiceImpl.java`
- `src/main/java/com/splatage/wild_economy/exchange/stock/StockService.java`
- `src/main/java/com/splatage/wild_economy/exchange/stock/StockServiceImpl.java`
- `src/main/java/com/splatage/wild_economy/bootstrap/ServiceRegistry.java`
- `src/main/java/com/splatage/wild_economy/bootstrap/PluginBootstrap.java`

### Admin/build
- `src/main/java/com/splatage/wild_economy/catalog/admin/AdminCatalogActionService.java`
- `src/main/java/com/splatage/wild_economy/catalog/admin/AdminCatalogPhaseOneService.java`
- `src/main/java/com/splatage/wild_economy/command/ShopAdminCommand.java`
- `src/main/resources/policy-rules.yml`
- `src/main/resources/policy-profiles.yml`
- `src/main/resources/manual-overrides.yml`
- `src/main/resources/root-values.yml`
- `src/main/resources/stock-profiles.yml`
- `src/main/resources/eco-envelopes.yml`
- `src/main/resources/exchange-items.yml`

---

## 17. Final Authority Statement

For the pricing/runtime catalog simplification scope, this document is the canonical source of truth.

Where existing code, previous docs, or legacy generated formats conflict with this document, this document wins.
