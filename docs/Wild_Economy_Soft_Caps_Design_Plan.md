# wild_economy — Soft Sell Caps + Atomic Buy Design Update

## Purpose

This document updates the current design direction for `wild_economy` to match the intended economics model:

* **Sell-side stock caps are soft pricing anchors, not hard intake blockers.**
* **Buy-side stock consumption must be atomic to prevent overselling.**
* **A player buy click is treated as one atomic unit, with a maximum purchase size of 64.**
* **A player sell action may be priced from the initial stock snapshot for that action, even when selling a large batch of one item.**

This aligns the technical design with the desired gameplay behavior: predictable buying, responsive stock-sensitive selling, and simple supply/demand feedback from real player activity.

---

## Updated design position

### 1. Sell-side stock model

For `PLAYER_STOCKED` items, `stock-cap` is **not** a hard maximum stock ceiling.

Instead, `stock-cap` is the point at which the sell-price taper reaches its configured floor.

Behavior:

* players may continue to sell beyond the configured cap
* the Exchange continues to accept stock
* the sell value falls as stock approaches the cap
* once stock is at or above the cap, the sell value remains at the minimum configured sell band / floor
* high stock should reduce reward, not reject player interaction

This means the Exchange behaves like a soft supply/demand system:

* low stock rewards useful production
* saturated stock discourages further oversupply through price pressure
* real stock levels still matter for later buyers

### 2. Sell quoting model

For a single sell action, it is acceptable to quote the action from the **initial stock snapshot** and use that rate for the whole accepted batch.

This applies to:

* `/shop sellhand`
* `/shop sellall`
* `/shop sellcontainer`

Design consequence:

* no intra-action repricing is required for v1
* no per-slot taper recalculation is required for a single sell action
* no partial intake is required for stock-cap reasons

This keeps sell behavior simple, understandable, and fast.

### 3. Buy-side stock model

Buy-side stock handling must be **atomic** for `PLAYER_STOCKED` items.

Current design intent:

* a player buy click is one atomic stock-consume action
* the largest atomic buy unit needed for player interaction is **64 items**
* a purchase must not be accepted unless the stock consume succeeds atomically
* other stock consumers (for example turnover or future admin operations) must not bypass the same stock mutation rules in ways that permit overselling

The goal is not to build a complex market engine. The goal is simply to ensure that two buyers cannot both receive the same stock.

### 4. Buy UX limit

The maximum per-click buy amount should remain **64**.

That is sufficient because:

* it matches normal Minecraft stack expectations for most bulk items
* larger purchases already require repeated interaction
* atomic correctness only needs to hold at the unit actually purchased in one action

---

## Required documentation updates

## README.md

### Keep

The current README direction is mostly correct and should remain aligned with the intended design:

* soft-cap anchoring
* graduated sell-value taper
* memory-first runtime stock
* no hard sell blocking purely because stock is high

### Clarify

Add a short explicit statement that:

* soft stock caps do **not** reject player sells
* buy-side stock for player-stocked items is consumed atomically per purchase action
* the maximum atomic player buy action is 64 items

Suggested wording:

> For `PLAYER_STOCKED` items, stock caps are soft pricing anchors rather than hard intake ceilings. Players may continue selling above the cap, but sell value will taper down to the configured floor. Buy-side stock consumption is atomic per purchase action to prevent overselling.

---

## docs/technical-spec_v1.md

### Update section 1 summary

Replace wording that implies a hard finite cap model.

Old direction:

* player-stocked items use stock cap, turnover/drain, smooth sell-price taper

New direction:

* player-stocked items use stock anchors, turnover/drain, and smooth sell-price taper tied to stock saturation

### Update section 5 “Stock model”

Replace the hard-buffer language.

Current direction says:

* all `PLAYER_STOCKED` items use a finite stock buffer
* player sells fill the stock buffer
* turnover reopens room

Replace with:

* all `PLAYER_STOCKED` items track live stock
* `stock-cap` is a pricing anchor, not a hard sell limit
* player sells increase live stock without cap-based rejection
* player purchases and turnover reduce live stock
* turnover helps desaturate stock over time, but is not required to “reopen room” for selling

### Update section 6 “Sell pricing”

Keep the stock-sensitive taper model, but clarify:

* near/full stock does **not** imply sell rejection
* at or above cap, the sell value stays at the minimum configured band/floor
* v1 may quote an entire sell action from the initial stock snapshot for simplicity

Suggested addition:

> For v1, a sell action may be quoted from the initial stock snapshot for that action rather than repricing progressively within the same command execution.

### Update section 7 “Sell UX requirements”

Clarify that feedback may note reduced value due to stock saturation, but not cap-based rejection.

### Update service responsibilities

Update `StockService` responsibilities from:

* enforce stock cap rules

To:

* provide atomic stock consumption for buy-side player-stocked purchases
* increment stock on sell without hard cap rejection
* resolve stock snapshots/state for pricing and GUI

Update `ExchangeSellService` responsibilities to reflect:

* action-level sell quoting from the starting stock snapshot is acceptable in v1
* no stock-room enforcement is required for soft-cap economics

Update `ExchangeBuyService` responsibilities to reflect:

* consume stock atomically before finalizing a player-stocked purchase
* avoid overselling under concurrent purchase attempts

---

## docs/database-design.md

### Update runtime stock responsibilities

Current text still includes:

* sell room checks
  n
  Replace with:

* stock-sensitive sell pricing inputs

* stock-sensitive buy eligibility

* turnover adjustments

* GUI stock visibility/state

### Update buy/sell write-path design

Revise the conceptual order.

#### Buy flow

Old conceptual order is too loose because it allows validation and gameplay action before secure stock consumption.

New conceptual order:

1. Read catalog entry from memory.
2. Read stock snapshot from memory for quoting/display.
3. Validate that the purchase is allowed.
4. Validate player balance and inventory space.
5. Atomically consume the requested stock amount for `PLAYER_STOCKED` items.
6. Apply economy and gameplay action.
7. If the buy cannot complete after stock consume, roll back the stock mutation.
8. Enqueue async stock persistence.
9. Enqueue async transaction log write.

#### Sell flow

Replace stock-room language with soft-cap language:

1. Read catalog entry from memory.
2. Read current stock from memory.
3. Validate sell eligibility.
4. Compute the stock-sensitive sale result from the initial stock snapshot.
5. Apply gameplay/economy action.
6. Mutate in-memory stock immediately.
7. Enqueue async stock persistence.
8. Enqueue async transaction log write.

### Update anti-patterns

Remove or rewrite any mention that implies hard cap rejection on sell.

Add explicit anti-pattern:

* buy flows that validate stock from a snapshot and later decrement stock in a separate non-atomic step

### Update integrity direction

Make the near-term runtime hardening priority explicit:

* atomic in-memory stock consumption for buy-side correctness comes before deeper SQL-side mutation hardening

---

## docs/wild_economy-v1_scope.md

Add one short clarification under goals or design philosophy:

* stock-sensitive selling should respond to saturation through value taper, not through hard sell rejection at cap

This keeps the scope document aligned with the current gameplay philosophy.

---

## docs/sell-container-feature-scope.md

No major conceptual change is required, but the document should stay consistent with the updated economics language:

* selling container contents should still use the same soft-cap stock-sensitive pricing model
* container selling should not introduce hard stock-cap rejection logic

---

## Implementation design plan

## Goal

Deliver a clean and minimal hardening pass that:

1. preserves soft-cap sell economics
2. removes buy-side oversell risk
3. avoids unnecessary complexity in sell pricing
4. keeps Folia-safe runtime behavior and memory-first stock ownership

---

## Core runtime rules

### Rule 1 — Sell caps are soft

`stock-cap` is a pricing anchor only.

Do not:

* reject sells because stock is already at or above cap
* partially accept sells because room is exhausted
* treat turnover as mandatory room creation

### Rule 2 — One sell action can use one starting quote

For a single sell action of one item key:

* use the starting stock snapshot
* calculate one unit payout
* apply that unit payout to the whole sold quantity for that action
* add stock once after the action completes

This is intentionally simple and acceptable for v1.

### Rule 3 — One buy click must consume stock atomically

For `PLAYER_STOCKED` items:

* a buy action must first secure stock atomically
* if stock cannot be secured, the purchase fails cleanly
* if later steps fail, stock must be restored

### Rule 4 — Largest atomic player buy unit is 64

No need to design player buy atomicity around larger numbers unless a future bulk-buy UX is added.

---

## Proposed service/API changes

## StockService

Add an atomic buy-side method for player-stocked items.

Suggested shape:

* `StockConsumeResult tryConsume(ItemKey itemKey, int amount)`

Behavior:

* atomically succeeds only if enough stock exists
* returns success/failure and resulting snapshot/state as needed
* never floors a failed consume to zero after the fact

Add a rollback helper if needed:

* `void restoreConsumed(ItemKey itemKey, int amount)`

Keep normal non-atomic snapshot reads for pricing/display:

* `StockSnapshot getSnapshot(ItemKey itemKey)`

Keep sell-side addition simple:

* `void addStock(ItemKey itemKey, int amount)`

No hard-cap intake enforcement is required.

---

## ExchangeBuyService

Revise player-stocked buy flow to:

1. validate player, item, amount
2. enforce max amount `<= 64`
3. quote using current snapshot
4. validate balance and inventory space
5. atomically consume stock for player-stocked items
6. withdraw money
7. give items
8. on failure after consume, restore stock and refund as needed
9. log purchase

Important requirement:

* do not give items first and remove stock later

---

## ExchangeSellService

Revise sell flow assumptions to:

1. gather sellable quantities by item key
2. read starting stock snapshot per sold item key
3. compute one quote per item key from that starting snapshot
4. remove sold items
5. pay player
6. add stock once per item key
7. log sale

Design note:

* for `/sellall`, if multiple stacks of the same key are sold in one action, they may all use the same starting rate for that action
* this is intentional v1 behavior, not a bug

---

## PricingService

Keep the taper model simple and explainable.

Desired semantics:

* stock below cap => better sell value
* stock near cap => reduced sell value
* stock at/above cap => minimum sell value floor

No need to add complicated live-curve math in this slice.

---

## Concurrency / correctness notes

### Buy-side

This is the main correctness target.

The current unsafe pattern to eliminate is:

* read snapshot
* check availability
* charge player
* give item
* decrement stock later

That permits overselling under concurrent buyers.

Replace it with:

* read/quote
* atomically consume
* finalize or roll back

### Sell-side

Soft-cap sell design means full atomic sell reservation is not needed for pricing correctness in v1.

Concurrent sells may still slightly blur the exact “perfect market” price under overlap, but that is acceptable because:

* the Exchange is not intended to be a high-frequency trading simulator
* sell pricing is intentionally approximate and player-friendly
* the main trust-critical risk is overselling on buys, not micro-precision on saturated sell quotes

---

## Suggested implementation order

### Phase 1 — Documentation alignment

Update README and core docs so the repo has one consistent story:

* soft sell caps
* action-level sell quoting
* atomic player-stocked buys

### Phase 2 — Buy-side stock hardening

Implement atomic `tryConsume(...)` in the stock layer and switch `ExchangeBuyServiceImpl` to use it.

This is the highest-value correctness fix.

### Phase 3 — Clean up sell-side language and logic

Remove any leftover “room” or “hard cap” assumptions from sell flows and stock service naming.

### Phase 4 — Optional polish

Add clearer player/admin feedback such as:

* “price reduced due to saturated stock” messaging
* stock state labels in GUI/detail view
* optional metrics for failed atomic buy attempts

---

## Acceptance criteria

The design update is complete when all of the following are true:

1. README and docs consistently describe stock caps as soft pricing anchors.
2. No core technical doc says sell actions must have available room.
3. Buy-side player-stocked purchases consume stock atomically.
4. Two buyers cannot both successfully buy the same remaining stock.
5. Players may still sell beyond the configured stock cap.
6. Sell value reaches a floor at/above cap rather than rejecting the sell.
7. A single sell action may use the starting quote for the whole batch without being considered drift.

---

## Recommended short repo wording

Use this sentence consistently across docs where needed:

> For `PLAYER_STOCKED` items, stock caps are soft pricing anchors rather than hard sell ceilings. Sell value tapers as stock approaches the cap and floors at the minimum configured value once the cap is reached or exceeded. Buy-side stock consumption remains atomic to prevent overselling.
