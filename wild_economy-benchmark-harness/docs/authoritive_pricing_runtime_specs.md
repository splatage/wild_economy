# wild_economy — Authoritative Pricing Runtime Engineering Spec

Revision date: 2026-03-24  
Status: Current pricing/runtime source of truth for the repo state after shipped-defaults and filing-hint patches  
Audience: maintainers and implementers of `wild_economy`

---

## 1. Purpose

This document defines the canonical runtime pricing model, the admin generation/publish lifecycle, and the file-role boundaries that prevent catalog drift.

It exists to keep four concerns clearly separated:

- **runtime catalog** consumed by `/shop`
- **admin/build inputs** used to generate that runtime catalog
- **generated review artifacts** used by operators
- **shipped defaults** that seed the admin/build inputs

---

## 2. Locked decisions

### 2.1 Single runtime catalog file

`exchange-items.yml` is the **only** catalog/pricing file consumed by the runtime `/shop` path.

Runtime `/shop` must not depend on:

- `root-values.yml`
- `policy-rules.yml`
- `policy-profiles.yml`
- `manual-overrides.yml`
- `stock-profiles.yml`
- `eco-envelopes.yml`
- `generated/generated-catalog.yml`
- any other generated intermediate artifact

### 2.2 Admin resolves reusable abstractions

Admin/catalog generation may use roots, rules, profiles, envelopes, recipe relationships, and classifier hints internally, but `/shopadmin catalog apply` must publish a fully resolved runtime catalog to `exchange-items.yml`.

### 2.3 Durable shipped defaults do not live in `exchange-items.yml`

`exchange-items.yml` is rewritten by `/shopadmin catalog apply`, so durable shipped defaults must live in generator inputs instead.

For the current design, that means:

- coverage/value anchors belong in `root-values.yml`
- filing/classification hints belong in `root-values.yml.layout.groups`
- broad policy assignment belongs in `policy-rules.yml`

### 2.4 Classifier consumes filing hints before heuristics

The category classifier must consult `root-values.yml.layout.groups` first and only fall back to built-in heuristics when no hint matches.

### 2.5 Published runtime catalog preserves generated filing

`/shopadmin catalog apply` writes both:

- `category`
- `generated-category`

into the published `exchange-items.yml` so the user-facing browse tree survives publish/apply.

---

## 3. Canonical lifecycle

### 3.1 Admin/build lifecycle

Inputs:

- `root-values.yml`
- `policy-rules.yml`
- `policy-profiles.yml`
- `manual-overrides.yml`
- `stock-profiles.yml`
- `eco-envelopes.yml`
- Bukkit material universe
- Bukkit recipe graph / fallback family relationships

Build flow:

1. load root/value anchors and layout hints
2. scan materials and recipe relationships
3. derive candidate values from anchored roots
4. classify using `layout.groups` first, then fallback heuristics
5. apply rules, profiles, and overrides
6. resolve stock/eco parameters into runtime-ready entries
7. emit generated review artifacts
8. on apply, publish the live runtime catalog to `exchange-items.yml`

Outputs:

- generated reports under `generated/` for admin review
- published live runtime catalog at `exchange-items.yml`

### 3.2 Runtime lifecycle

Startup/reload flow:

1. load `exchange-items.yml`
2. validate runtime schema
3. build immutable `ExchangeCatalog`
4. initialize stock service/cache
5. initialize pricing service
6. player `/shop` flows read the cached catalog only

Runtime quote flow:

1. lookup cached entry by `ItemKey`
2. lookup current stock snapshot
3. compute buy/sell quote from cached runtime parameters and stock
4. return quote

---

## 4. Authoritative file roles

### 4.1 Runtime-consumed file

#### `exchange-items.yml`

Role:
- published runtime catalog
- only catalog/pricing file consumed by `/shop`

Characteristics:
- fully resolved
- carries runtime-facing buy/sell/stock parameters
- includes `category` and `generated-category` when known
- contains no unresolved root/rule/profile/envelope dependency

### 4.2 Admin/build-only files

These files remain valid and important, but are admin/build inputs only:

- `root-values.yml`
- `policy-rules.yml`
- `policy-profiles.yml`
- `manual-overrides.yml`
- `stock-profiles.yml`
- `eco-envelopes.yml`

Runtime pricing/catalog services must not read them after publish.

### 4.3 Generated review/report files

Current generated/admin review artifacts include:

- `generated/generated-catalog.yml`
- `generated/generated-catalog-summary.yml`
- `generated/generated-summary.yml`
- `generated/generated-diff.yml`
- `generated/generated-validation.yml`
- `generated/item-decision-traces.yml`
- `generated/generated-rule-impacts.yml`
- `generated/generated-review-buckets.yml`

These are operator-facing review artifacts only. Runtime `/shop` must not depend on them.

---

## 5. `root-values.yml` contract

`root-values.yml` now carries two separate concerns.

### 5.1 `items`

Purpose:
- explicit economic anchors for root/basic items
- durable shipped coverage for items that do not derive cleanly from recipes

Characteristics:
- keep this section as a clean price list
- values are economic anchors, not browse taxonomy
- missing anchors block that derivation path

### 5.2 `layout.groups`

Purpose:
- admin-editable filing hints for generated browse categories
- durable shipped browse grouping that survives publish/apply

Matching behavior:
- exact `item-keys` win first
- wildcard `item-key-patterns` apply next
- classifier heuristics run only if no layout hint matches

Example shape:

```yml
items:
  minecraft:oak_log: 12.00
  minecraft:stone: 2.50
  minecraft:bone: 3.00

layout:
  groups:
    woods:
      generated-category: WOODS
      item-key-patterns:
        - "minecraft:*_log"
        - "minecraft:*_stem"

    mob-drops:
      generated-category: MOB_DROPS
      item-keys:
        - "minecraft:bone"
        - "minecraft:string"
```

---

## 6. Runtime schema — `exchange-items.yml`

Illustrative published runtime shape:

```yml
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

Required fields remain runtime-facing and resolved. The canonical runtime contract does **not** include admin-side references such as `stock-profile`, `eco-envelope`, or root-value lookups.

---

## 7. Canonical pricing contract

### 7.1 Runtime inputs

Runtime quote calculation may use only:

- cached `ExchangeCatalogEntry`
- current `StockSnapshot`
- requested amount

### 7.2 Buy pricing

Buy prices should remain:

- standardized
- stable
- predictable

Buy-side pricing is not intended to behave like a live market simulator.

### 7.3 Sell pricing

For `PLAYER_STOCKED` items:

- stock may rise above the configured pricing range
- sells are not hard-blocked at the cap
- sell value declines linearly between configured stock anchors
- one sell action is priced as one aggregated batch per item key
- batch payout is piecewise by range: full-price plateau, linear trapezoid, floor plateau
- rounding happens once at the end of the batch quote

### 7.4 Runtime path clarification

The canonical runtime pricing path does **not** use `root-values.yml`, `stock-profiles.yml`, or `eco-envelopes.yml` directly. Those remain admin/build inputs used to publish `exchange-items.yml`.

---

## 8. Documentation precedence

When documents disagree, use this order:

1. code behavior in the current repo
2. this file
3. `docs/technical-spec_v1.md`
4. `docs/wild_economy_admin_design_and_specs_updated.md`
5. historical/superseded docs

Older docs that describe `exchange-items.yml` as the durable shipped-default source or omit `root-values.yml.layout.groups` should be treated as historical only.
