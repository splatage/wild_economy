# wild_economy — Repo-Ready Technical Spec (v1)

Revision date: 2026-03-23  
Repo snapshot: `016f204299469b899359f9057ad4e0b1cde8d7e5`  
Status: Current intended v1 technical spec aligned to the repo direction and current pricing QC corrections.

This document is the intended implementation-stage source of truth for `wild_economy` v1.

It reflects the current direction to date:

- Exchange-first, and likely Exchange-only in v1
- player-stocked trade is the primary economic path
- unlimited buy remains a narrow exception
- disabled items protect progression and server identity
- buying is GUI-driven
- selling is command-driven
- the catalog is built from a generated base plus explicit overrides
- root values are anchored in `root-values.yml`
- item derivation is recipe-graph based
- generated catalog output is merged with explicit overrides for runtime use
- player-stocked items use soft stock-cap anchoring, turnover, stock-sensitive sell taper, and atomic buy-side stock consumption
- pricing QC has removed `initialStock` from the canonical pricing model
- pricing QC has removed `stock-profiles.yml` from the canonical pricing path
- admin/catalog work is now moving toward a staged preview/validate/diff/apply pipeline, but that full admin workflow is not yet complete in code

---

## 1. Product and naming conventions

### Plugin identity

- **Plugin/project name:** `wild_economy`
- **Java package root:** `com.splatage.wild_economy`
- **Player-facing base command:** `/shop`

### Internal terminology

Use **Shop** for player-facing command and menu language.  
Use **Exchange** internally for the business/domain model.

Examples:

- Player command: `/shop sellall`
- Internal service: `ExchangeSellService`
- Internal menu class: `ExchangeBrowseMenu`

This keeps the player UX intuitive while preserving clean domain language in code.

---

## 2. Scope boundaries

### Included in v1

- curated Exchange item catalog
- item policy system
- generated base catalog from `root-values.yml` and recipe derivation
- explicit override layer merged last
- player-stocked items
- unlimited-buy exception items
- disabled items
- soft stock-cap anchors for player-stocked items
- background stock turnover/drain
- stock-sensitive sell-price taper
- stable, standardized buy pricing
- immediate payout on sell
- GUI browsing and buying
- command-driven selling
- SQLite support
- MySQL/MariaDB support
- transaction logging sufficient for support/debugging
- file-driven admin/catalog review pipeline work in phased form

### Excluded from v1

- Marketplace as a core system
- auctions
- bids/offers/negotiation
- dynamic buy-side market pricing
- public works integration
- fuzzy item matching
- broad support for every item by default
- progression-sensitive items in easy circulation
- broad admin dashboards as part of the initial runtime delivery
- full GUI rule editing in the initial admin/catalog Phase 1
- automatic publish of generated catalog proposals

---

## 3. Core design rules

1. **Support gameplay; do not shortcut it.**
2. **Reward useful player production.**
3. **Keep item circulation intentional and policy-driven.**
4. **Keep buy-side predictable.**
5. **Let sell-side respond to saturation in a controlled way.**
6. **Keep GUI thin.**
7. **Keep item normalization strict.**
8. **No blocking database work in GUI click paths.**
9. **No raw SQL in service classes.**
10. **No Bukkit `Player` leakage into repositories.**
11. **Soft stock caps reduce value; they do not hard-block sells.**
12. **Player-stocked buy consumption must be atomic.**
13. **The runtime catalog must be deterministic and explainable.**
14. **Admin/catalog generation should happen off the normal player interaction path.**
15. **Human-readable files remain the admin source of truth.**

---

## 4. Catalog architecture

### 4.1 High-level model

The runtime catalog is built from two layers:

1. a **generated base catalog**
2. an **explicit override layer** that wins last

### 4.2 Root values and derivation

`root-values.yml` is the anchor source for base item values.

Generation uses a recipe graph to:

- discover craft relationships
- derive candidate values for non-root items from anchored roots
- classify materials
- suggest initial policy treatment
- emit generated catalog files for review and runtime import

### 4.3 Generated outputs

The current generator writes proposal artifacts under a generated output area.

At the current repo snapshot, the implemented outputs are:

- `generated/generated-catalog.yml`
- `generated/generated-catalog-summary.yml`

These files are part of the current generator path. The fuller staged publish/diff/snapshot workflow is planned admin work, not complete runtime behavior yet.

### 4.4 Override layer

Explicit item decisions and corrections are currently carried by the runtime override config layer.

That layer is used for:

- exact per-item corrections
- wildcard-driven bulk rules
- final override of generated defaults

### 4.5 Runtime merge rule

The intended runtime merge rule is:

1. root values anchor derivation
2. generated catalog provides the default structured base
3. explicit overrides win last

This gives a scalable catalog without forcing hand-maintenance of every item.

---

## 5. Item policy model

### 5.1 Runtime-facing policy states

The current runtime remains centered on these core economic modes:

- `PLAYER_STOCKED`
- `UNLIMITED_BUY`
- `DISABLED`

These represent the actual runtime economic behavior.

### 5.2 Admin-facing policy groups

The admin/catalog generation direction now uses four higher-level policy groups:

- `ALWAYS_AVAILABLE`
- `EXCHANGE`
- `SELL_ONLY`
- `DISABLED`

These are admin intent states. They may be mapped into current runtime flags and modes during import.

### 5.3 Policy meanings

#### `PLAYER_STOCKED` / `EXCHANGE`

Default and primary mode.

Used for whitelisted, standardized bulk goods, especially useful farm outputs and server-useful materials.

Behavior:

- players can sell these items to the Exchange
- players are paid immediately
- sold items become Exchange stock
- other players can buy from that stock
- soft stock caps taper sell value as stock saturates
- sell intake is not hard-blocked just because stock is already high

Purpose:

- reward useful production
- tie trade to real supply
- circulate practical goods without conjuring stock

#### `UNLIMITED_BUY` / `ALWAYS_AVAILABLE`

Narrow exception mode.

Used for nuisance or world-damaging materials where the design goal is to reduce ugly harvesting and landscape damage.

Current pricing-path behavior:

- players can buy these items regardless of stock
- they are not part of the stock-sensitive sell taper path
- supply is server-provided by policy

Likely examples:

- sand
- red sand
- ice
- selected logs

#### `SELL_ONLY`

Admin-facing intent state used where admins want to reward collection, disposal, or cleanup without turning the item into a normal public stock-backed buy item.

This remains an admin/catalog capability rather than a large runtime expansion in early v1.

#### `DISABLED`

Used for progression-sensitive or shortcutting items.

Behavior:

- not buyable
- not sellable

Purpose:

- preserve progression
- keep rare, unsafe, or identity-breaking items out of easy circulation

---

## 6. Stock model

All player-stocked items track live stock.

Each catalog entry may define:

- `stock-cap`
- turnover amount and interval
- stock-state thresholds
- reusable eco-envelope references with resolved taper controls in the runtime catalog

### Behavior

- player sells increase live stock
- player purchases drain live stock quickly
- background turnover drains live stock slowly over time
- `stock-cap` acts as a pricing anchor, not a hard sell ceiling
- at or above the cap, sell value floors at the configured minimum price floor

### Design intent

This acts like a **soft-capped / leaky-bucket stock model**:

- bursty production can be absorbed
- player demand drains stock naturally
- background turnover helps desaturate stock even if player demand is weak
- stock can rise above cap, but oversupply is discouraged through lower sell value rather than rejection

### Important interpretation

Background turnover is **not merely cleanup**.

It is a deliberate form of **passive server-side consumption / turnover**.

### Canonical pricing-path clarification

The canonical runtime pricing model does **not** use `initialStock`.

The canonical runtime pricing path also does **not** rely on `stock-profiles.yml`.
Compatibility structures may still exist in the repo while cleanup continues, but they are not the pricing source of truth.

---

## 7. Pricing model

### 7.1 Source of truth

The economic anchor is no longer described as direct runtime dependence on Essentials `worth.yml`.

The intended v1 source flow is:

1. anchor root/basic values in `root-values.yml`
2. derive generated values through recipe relationships
3. merge generated defaults with explicit overrides
4. use the internal runtime catalog for all runtime pricing logic

Runtime code should use the internal catalog, not repeatedly consult an external worth file.

### 7.2 Buy pricing

Buy prices must remain:

- standardized
- stable
- predictable

Buy-side pricing should **not** behave like a live market simulator.

### 7.3 Sell pricing

Sell prices taper downward as stock fills.

Meaning:

- low stock = better sell value
- high stock = reduced sell value
- at or above cap = minimum configured sell value floor
- soft caps reduce reward; they do not reject the sell

### 7.4 Batch quote model

For v1, sell pricing is calculated **per item key batch**, not per slot.

For one sell action of one item key:

- aggregate the total amount first
- read the starting stock snapshot once
- compute payout from the start stock and end stock
- if the batch stays below the taper floor range, use the average of start price and end price
- if the batch crosses the cap, split the payout into:
  - a tapering segment up to the cap
  - a floor-priced segment beyond the cap
- round once at the end of the batch quote

This keeps pricing understandable while preventing large same-action sells from being overpaid at the initial rate.

### 7.5 Reusable envelope model

The taper shape is driven by reusable eco envelopes.

At the canonical pricing level, the effective sell behavior is determined by:

- root/base worth
- current live stock
- `stock-cap`
- reusable eco envelope parameters
- resolved floor behavior in the runtime catalog

This keeps the pricing model reusable without requiring per-item legacy price-band config.

---

## 8. Player interaction model

### 8.1 Buying

Buying is GUI-driven.

Player flow:

1. `/shop`
2. browse categories
3. browse items
4. open item detail
5. select quantity
6. confirm buy

The GUI should show:

- item name
- buy price
- stock state
- available stock where relevant

### Buy correctness rules

For player-stocked items:

- per-click buy amount is limited
- stock must be consumed atomically before finalizing the purchase
- the system must not allow two buyers to receive the same remaining stock

### 8.2 Selling

Selling is command-driven.

Primary commands:

- `/shop sellhand`
- `/shop sellall`
- `/shop sellcontainer`

This is deliberate because selling is a high-frequency action and GUI selling would be clunkier than command use.

### Sell UX requirements

Sell feedback must clearly report:

- what sold
- how much was earned
- which items were skipped
- whether any items sold at reduced value due to saturation

Nothing should be silently lost or skipped.

---

## 9. Service responsibilities

### `StockService`

Responsibilities:

- provide stock snapshots for pricing and GUI display
- mutate stock in memory immediately during gameplay actions
- provide atomic buy-side stock consumption for player-stocked items
- provide non-blocking stock addition on sell
- support turnover drain
- enqueue persistence asynchronously after mutation

### `ExchangeBuyService`

Responsibilities:

- validate player, item, and amount
- enforce max buy size
- quote the purchase
- perform atomic stock consume for player-stocked items
- finalize or reject the purchase cleanly
- avoid overselling

### `ExchangeSellService`

Responsibilities:

- scan inventory/container contents
- normalize and validate items
- aggregate by item key
- quote one batch per item key
- mutate stock once per item key
- report sold and skipped results clearly

### `PricingService`

Responsibilities:

- produce stable buy quotes
- produce stock-sensitive sell quotes
- implement the batch-average/trapezoid sell model
- apply a floor split when a batch crosses the soft cap
- round once at the end of each sell batch quote

### Catalog/admin generation services

Responsibilities:

- scan the material universe
- build recipe relationships
- resolve anchored derivations
- classify materials
- suggest policy treatment
- emit generated catalog proposal files
- stay off the hot runtime interaction path

---

## 10. Mandatory domain objects

### `ItemKey`

Canonical Exchange key.

Example values:

- `minecraft:wheat`
- `minecraft:cactus`
- `minecraft:oak_log`

This must be strict. No fuzzy matching in v1.

### `ItemPolicyMode`

Current core runtime enum:

- `PLAYER_STOCKED`
- `UNLIMITED_BUY`
- `DISABLED`

Admin/catalog work may use a richer intent layer above this.

### `ExchangeCatalogEntry`

This is the central runtime config object.

Each entry should capture at minimum:

- item key
- display name
- category / grouping info
- policy mode
- base worth / anchor-derived price basis
- buy price
- sell price
- resolved runtime sell envelope / taper settings
- stock cap
- turnover settings
- buy enabled
- sell enabled

### `StockSnapshot`

Used by pricing and GUI view logic.

Should include:

- item key
- current stock
- stock cap
- fill ratio
- stock state

### `BuyQuote` / `SellQuote`

Structured pricing outputs.

Sell quote should include:

- base unit price
- effective unit price
- total payout
- fill ratio
- whether tapering was applied

### Result models

Use structured result objects rather than booleans.

At minimum:

- `SellHandResult`
- `SellAllResult`
- `BuyResult`
- `SellLineResult`
- `SellContainerResult`

---

## 11. Admin/catalog direction for v1

The current admin/catalog direction is deliberately file-driven and staged.

### Phase 1 admin/catalog deliverables

The locked Phase 1 admin/catalog slice is:

1. `policy-rules.yml`
2. `manual-overrides.yml`
3. `eco-envelopes.yml`
4. `/shopadmin catalog preview`
5. `/shopadmin catalog validate`
6. `/shopadmin catalog diff`
7. `/shopadmin catalog apply`
8. generated summary + diff reports
9. item decision trace backend

Phase 1 remains intentionally modest. It is about making the generated catalog explainable, reviewable, and safely applicable rather than building a broad admin UI first.

