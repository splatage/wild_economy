# wild_economy — Repo-Ready Technical Spec (v1)

## Status

This document is the **current implementation-stage source of truth** for `wild_economy` v1.

It reflects the locked direction to date:

- **Exchange-first, likely Exchange-only**
- **Player-stocked** is the default item mode
- **Unlimited buy-only** is a narrow exception
- **Disabled** protects progression and server identity
- **Buying is GUI-driven**
- **Selling is command-driven**
- Prices are sourced from **Essentials `worth.yml`**, imported into an **internal catalog**
- Player-stocked items use:
  - soft stock-cap anchoring
  - background turnover/drain
  - stock-sensitive sell-price taper as stock saturates
  - atomic buy-side stock consumption

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

## Included in v1

- Curated Exchange item catalog
- Item policy system
- `PLAYER_STOCKED` items
- `UNLIMITED_BUY` items
- `DISABLED` items
- Soft stock-cap anchors for player-stocked items
- Background stock turnover/drain
- Stock-sensitive sell-price taper
- Stable, standardized buy pricing
- Immediate payout on sell
- GUI browsing and buying
- Command-driven selling
- SQLite support
- MySQL/MariaDB support
- Transaction logging sufficient for support/debugging

## Excluded from v1

- Marketplace as a core system
- Auctions
- Bids/offers/negotiation
- Dynamic buy-side market pricing
- Public works integration
- Fuzzy item matching
- Broad support for every item by default
- Progression-sensitive items in easy circulation
- Large admin dashboards / analytics suites

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

---

## 4. Item policy model

## `PLAYER_STOCKED`

Default and primary mode.
Used for whitelisted, standardized bulk goods, especially useful farm outputs and server-useful materials.

Behavior:

- Players can sell these items to the Exchange
- Players are paid immediately
- Sold items become Exchange stock
- Other players can buy from that stock
- Soft stock caps taper sell value as stock saturates
- Sell intake is not hard-blocked just because stock is already high

Purpose:

- Reward useful production
- Tie trade to real supply
- Circulate practical goods without conjuring stock

## `UNLIMITED_BUY`

Narrow exception mode.
Used for nuisance or world-damaging materials where the design goal is to reduce ugly harvesting and landscape damage.

Behavior:

- Players can buy these items
- They are not part of the normal sell-back loop
- Supply is server-provided by policy

Likely examples:

- sand
- red sand
- ice
- logs

## `DISABLED`

Used for progression-sensitive or shortcutting items.

Behavior:

- Not buyable
- Not sellable

Purpose:

- Preserve progression
- Keep rare/important items out of easy circulation

---

## 5. Stock model

All `PLAYER_STOCKED` items track live stock.

Each catalog entry may define:

- `stock-cap`
- `turnover-amount-per-interval`
- stock-state thresholds
- sell-price bands

### Behavior

- Player sells increase live stock
- Player purchases drain live stock quickly
- Background turnover drains live stock slowly over time
- `stock-cap` acts as a pricing anchor, not a hard sell ceiling
- At or above the cap, sell value floors at the configured minimum band

### Design intent

This acts like a **soft-capped / leaky-bucket stock model**:

- bursty production can be absorbed
- player demand drains stock naturally
- background turnover helps desaturate stock even if player buy demand is weak
- stock can rise above cap, but oversupply is discouraged through lower sell value rather than rejection

### Important interpretation

Background turnover is **not merely cleanup**.
It is a deliberate form of **passive server-side consumption / turnover**.

---

## 6. Pricing model

## Source of truth

Use Essentials `worth.yml` as the base standardized value source.

At startup/reload:

- import values from `worth.yml`
- merge them with explicit item config
- build an internal Exchange catalog

Runtime logic must use the internal catalog, not poke through `worth.yml` repeatedly.

## Buy pricing

Buy prices must remain:

- standardized
- stable
- predictable

Buy-side pricing should **not** behave like a live market simulator.

## Sell pricing

Sell prices taper downward as stock fills.

Meaning:

- low stock = better sell value
- high stock = reduced sell value
- at or above cap = minimum configured sell value floor
- soft caps reduce reward; they do not reject the sell

### Batch quote model

For v1, sell pricing is calculated **per item key batch**, not per slot.

For one sell action of one item key:

- aggregate the total amount first
- read the starting stock snapshot once
- compute payout from the start stock and end stock
- if the batch stays below cap, use the average of start price and end price
- if the batch crosses the cap, split the payout into:
  - a tapering segment up to the cap
  - a floor-priced segment beyond the cap
- round once at the end of the batch quote

This keeps pricing understandable while preventing large same-action sells from being overpaid at the initial rate.

### Recommended v1 bands

Example only:

- 0.00–0.25 fill → 100%
- 0.25–0.50 fill → 85%
- 0.50–0.75 fill → 65%
- 0.75–0.90 fill → 40%
- 0.90–1.00+ fill → 20%

This is intentionally simpler and more explainable than a formula-heavy curve.

---

## 7. Player interaction model

## Buying

Buying is GUI-driven.

Player flow:

1. `/shop`
2. Browse categories
3. Browse items
4. Open item detail
5. Select quantity
6. Confirm buy

The GUI should show:

- item name
- buy price
- stock state
- available stock

### Buy correctness rules

For `PLAYER_STOCKED` items:

- per-click buy amount is limited to 64
- stock must be consumed atomically before finalizing the purchase
- the system must not allow two buyers to receive the same remaining stock

## Selling

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

## 8. Service responsibilities

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

---

## 9. Mandatory domain objects

## `ItemKey`

Canonical Exchange key.

Example values:

- `minecraft:wheat`
- `minecraft:cactus`
- `minecraft:oak_log`

This must be strict. No fuzzy matching in v1.

## `ItemPolicyMode`

Initial v1 enum:

- `PLAYER_STOCKED`
- `UNLIMITED_BUY`
- `DISABLED`

`SELL_ONLY` is intentionally left out of the core v1 path unless later reintroduced deliberately.

## `ExchangeCatalogEntry`

This is the central runtime config object.

Each entry must capture:

- item key
- display name
- category
- policy mode
- base worth
- buy price
- sell price
- stock cap
- turnover amount per interval
- sell-price bands
- buy enabled
- sell enabled

## `StockSnapshot`

Used by pricing and GUI view logic.
Should include:

- item key
- current stock
- stock cap
- fill ratio
- stock state

## `BuyQuote` / `SellQuote`

Structured pricing outputs.

Sell quote must include:

- base unit price
- effective unit price
- total payout
- fill ratio
- whether tapering was applied

## Result models

Use structured result objects rather than booleans.
At minimum:

- `SellHandResult`
- `SellAllResult`
- `BuyResult`
- `SellLineResult`
- `SellContainerResult`

---

## 10. Runtime expectations

The runtime design priority is:

1. keep gameplay paths synchronous and predictable
2. keep database persistence off the main interaction path
3. ensure buy-side stock correctness before deeper optimization work
4. prefer clean per-item-key batch handling over noisy per-slot work

This means:

- pricing math stays on the gameplay path
- inventory/economy work stays on the gameplay path
- stock persistence stays asynchronous
- transaction logging stays asynchronous

That is the intended v1 balance between correctness, performance, and simplicity.
