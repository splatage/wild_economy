# wild_economy — Soft Sell Caps + Atomic Buy Design Update

## Purpose

This document updates the current design direction for `wild_economy` to match the intended economics model:

- **Sell-side stock caps are soft pricing anchors, not hard intake blockers.**
- **Buy-side stock consumption must be atomic to prevent overselling.**
- **A player buy click is treated as one atomic unit, with a maximum purchase size of 64.**
- **A player sell action is priced as one aggregated batch per item key using the batch-average / trapezoid model.**

This aligns the technical design with the desired gameplay behavior: predictable buying, responsive stock-sensitive selling, and simple supply/demand feedback from real player activity.

---

## Updated design position

### 1. Sell-side stock model

For `PLAYER_STOCKED` items, `stock-cap` is **not** a hard maximum stock ceiling.

Instead, `stock-cap` is the point at which the sell-price taper reaches its configured floor.

Behavior:

- players may continue to sell beyond the configured cap
- the Exchange continues to accept stock
- the sell value falls as stock approaches the cap
- once stock is at or above the cap, the sell value remains at the minimum configured floor
- high stock should reduce reward, not reject player interaction

This means the Exchange behaves like a soft supply/demand system:

- low stock rewards useful production
- saturated stock discourages further oversupply through price pressure
- real stock levels still matter for later buyers

### 2. Sell quoting model

For a single sell action:

- aggregate the accepted amount by item key first
- read the starting stock snapshot once
- compute payout from the starting stock and the ending stock for that batch
- use the average of start price and end price across the taper range
- if the batch crosses the cap, split payout into:
  - a tapering segment up to the cap
  - a floor-priced segment beyond the cap
- round once at the end of the batch quote

This applies to:

- `/shop sellhand`
- `/shop sellall`
- `/shop sellcontainer`

Design consequence:

- no per-slot taper recalculation is required
- no hard stock-cap rejection is required
- no partial intake is required for stock-cap reasons

This keeps sell behavior simple, understandable, and fast while avoiding overpayment on large single-action sells.

### 3. Buy-side stock model

Buy-side stock handling must be **atomic** for `PLAYER_STOCKED` items.

Current design intent:

- a player buy click is one atomic stock-consume action
- the largest atomic buy unit needed for player interaction is **64 items**
- a purchase must not be accepted unless the stock consume succeeds atomically
- other stock consumers (for example turnover or future admin operations) must not bypass the same stock mutation rules in ways that permit overselling

The goal is not to build a complex market engine.

The goal is simply to ensure that two buyers cannot both receive the same stock.

### 4. Buy UX limit

The maximum per-click buy amount should remain **64**.

That is sufficient because:

- it matches normal Minecraft stack expectations for most bulk items
- larger purchases already require repeated interaction
- atomic correctness only needs to hold at the unit actually purchased in one action

---

## Required documentation updates

### README.md

Keep:

- soft-cap anchoring
- graduated sell-value taper
- memory-first runtime stock
- no hard sell blocking purely because stock is high

Clarify:

- soft stock caps do **not** reject player sells
- buy-side stock for player-stocked items is consumed atomically per purchase action
- the maximum atomic player buy action is 64 items
- canonical pricing now uses reusable eco envelopes without `initialStock`

Suggested wording:

> For `PLAYER_STOCKED` items, stock caps are soft pricing anchors rather than hard intake ceilings. Players may continue selling above the cap, but sell value will taper down to the configured floor. Buy-side stock consumption is atomic per purchase action to prevent overselling.

### docs/technical-spec_v1.md

Update section summaries to reflect:

- stock anchors rather than hard finite stock buffers
- no `initialStock` in the canonical pricing model
- reusable eco envelopes as the pricing taper source
- aggregated batch-average / trapezoid payout per item key
- no claim that runtime pricing depends on `stock-profiles.yml`

Suggested technical wording:

> For v1, sell pricing is calculated per aggregated item-key batch from the starting stock and ending stock for that batch. If the batch stays inside the taper range, payout uses the average of start and end unit values. If the batch crosses the cap, payout is split into a tapering segment and a floor-priced segment, then rounded once at the end.

