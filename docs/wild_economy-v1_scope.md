# The Wild Exchange Plugin — Locked Scope v1

## 1. Product definition

A **GUI-first Minecraft Exchange plugin** designed to **support gameplay rather than shortcut it**.

The current direction is **Exchange-first and likely Exchange-only**.  

The Exchange exists to:
- reward **useful player production**
- circulate **practical, server-useful bulk goods**
- keep supply tied primarily to **real player work**
- avoid easy circulation of items that flatten progression

---

## 2. Design philosophy

The Exchange is not a generic “buy anything, sell anything” shop.

It is a **curated gameplay-shaping exchange**.

Its purpose is to:
- encourage players to build useful farms and production systems
- create a shared supply of practical goods for the wider server
- reduce destructive harvesting of selected nuisance materials
- keep progression-sensitive or unhealthy items out of easy circulation

The system should feel:
- clear
- fast
- predictable
- grounded in real activity
- elegant in both UX and code

---

## 3. Locked v1 goals

### Primary goals
- Reward useful farms and real production
- Let players buy practical bulk goods from real stock
- Keep buying simple and visual through GUI
- Keep selling fast and low-friction through commands
- Shape item circulation intentionally through policy
- Keep the codebase clean, modular, and performant

### Success criteria
- Players immediately understand what the Exchange is for
- Selling useful bulk goods feels rewarding and quick
- Buying stocked goods feels easy and predictable
- The Exchange does not become a progression shortcut machine
- The system performs well on live servers
- The code stays cleanly separated by domain

---

## 4. Locked v1 mechanics

### 4.1 Item policy model

Exchange behavior is driven by **item policy**.

#### A. Player-stocked
This is the **default and primary mode**.

Used for intentionally whitelisted, standardized bulk goods, especially useful farm outputs and practical server-useful materials.

Behavior:
- players can sell these items into the Exchange
- players are paid immediately on sale
- the items become Exchange stock
- other players can buy from that stock

Purpose:
- reward useful production
- keep trade tied to real supply
- circulate practical goods without conjuring stock from nowhere

#### B. Unlimited buy-only
This is a **narrow exception**.

Used for selected nuisance or world-damaging materials where the goal is to reduce ugly harvesting and landscape damage.

Typical examples:
- sand
- ice
- logs

Behavior:
- players can buy these items
- they are not intended to be a normal sell-back loop
- supply is server-provided by policy

Purpose:
- protect the world
- reduce bad harvesting incentives
- support builders without requiring environmental damage

#### C. Disabled
Used for progression-sensitive, shortcutting, or otherwise unhealthy items.

Behavior:
- not buyable
- not sellable

Purpose:
- preserve progression
- avoid flattening gameplay
- keep inappropriate goods out of easy circulation

#### D. Sell-only
This is **not a core mode** for v1 and is now secondary.

It may still exist for narrow edge cases, but much of its role is replaced by the capped player-stocked buffer model below.

---

### 4.2 Stock buffer model for player-stocked items

Player-stocked items use a **finite stock buffer** per item.

Each item has:
- a **stock cap**
- **fast drain** when players buy from the Exchange
- **slow background drain/turnover** over time

This acts like a **buffered / leaky-bucket stock model**:
- player production can fill the buffer
- player purchases drain it quickly
- background turnover slowly reopens room even if buy demand is weak

Purpose:
- avoid permanent stock saturation
- keep useful farms economically relevant
- throttle oversupply without harsh hard-lock behavior
- allow real circulation while still controlling eco throughput

Important design meaning:
the background drain is not just cleanup; it is a deliberate form of **passive server-side consumption / turnover**.

---

### 4.3 Pricing model

#### Price source
Use **Essentials `worth.yml`** as the standardized Exchange price source, but import it into an **internal Exchange catalog**.

`worth.yml` is a source input, not something that should be directly sprayed through runtime logic everywhere.

#### Buy prices
Buy prices should remain:
- standardized
- predictable
- stable

The buy side should not feel like a live market simulator.

#### Sell prices
Sell prices should **taper smoothly downward as stock fills**.

Meaning:
- lower stock = better sell value
- higher stock = lower sell value
- near full stock = low but still valid sell value, depending on policy

This should be a **smooth saturation curve**, not:
- a hard sell block
- a harsh cliff like “normal until full, then worthless”

Purpose:
- throttle oversupply gracefully
- let useful farms keep selling
- avoid ugly stop/start behavior at full stock

---

## 5. Locked player interaction model

### 5.1 Buying
Buying is **GUI-driven**.

The GUI is used to:
- browse stocked Exchange goods
- view item price
- view stock state
- purchase available stock

This is where the Exchange should feel visual, clear, and easy.

### 5.2 Selling
Selling is likely **command-driven**, because it is faster and less clunky for routine use.

Expected direction:
- `sellhand`
- `sellall`

Purpose:
- keep high-frequency selling fast
- avoid unnecessary menu friction
- make useful farm output easy to dump into the Exchange

So the intended interaction split is:

- **Exchange buy = GUI**
- **Exchange sell = commands**

---

## 6. Included in v1

### Core Exchange features
- curated Exchange item catalog
- item policy system
- player-stocked items
- unlimited buy-only items
- disabled items
- stock caps for player-stocked items
- background stock turnover/drain
- stock-sensitive sell-price taper
- standardized buy pricing
- immediate payout on sale
- real stock purchasing

### UX features
- GUI for browsing and buying
- stock display in GUI
- command-driven selling
- clear feedback on sell results
- clear feedback on buy results

### Technical features
- import pricing from `worth.yml` into internal catalog
- support for **SQLite**
- support for **MySQL/MariaDB**
- repository abstraction
- clean separation of GUI, domain logic, and persistence
- no blocking DB I/O on main-thread click paths

---

## 7. Excluded from v1

The following are outside locked v1 scope unless deliberately revisited later:

- full Marketplace as a core system
- dynamic buy-side market pricing
- auctions
- bids/offers/negotiation
- wishlists or favorites
- price history graphs
- storefronts
- bundles
- barter systems
- public works integration
- broad fuzzy Exchange matching
- support for every item by default
- progression-sensitive items in easy circulation
- giant admin suite or analytics-heavy dashboards

V1 should stay narrow and elegant.

---

## 8. Code architecture priorities

The implementation should be organized around clean boundaries.

### Core architectural requirements
- GUI/presentation must stay separate from business logic
- business logic must stay separate from persistence
- item normalization must be a first-class domain
- SQL/backend-specific behavior must not leak all through service code
- runtime code should use the internal Exchange catalog, not directly depend on `worth.yml`

### Persistence requirements
- same logical behavior on SQLite and MySQL/MariaDB
- repository abstraction
- clear schema/init/migration path
- no main-thread blocking in player-facing click paths

### Performance requirements
- Exchange open/browse should feel quick
- selling common goods should feel nearly instant
- expensive persistence or calculation work should not sit in click-paths on the server thread

---

## 9. Practical economic identity

The Exchange should primarily reward:

- useful farms
- practical bulk production
- goods that benefit the wider server

The Exchange should not primarily serve as:
- a universal item shortcut
- a progression bypass
- a rare-item bazaar

The intended identity is:

**Players earn eco by supplying real, useful stock.  
Other players buy from that real stock.  
The system selectively supports server health where needed through narrow exceptions.**

---

## 10. Current source-of-truth summary

The locked v1 direction is:

- **Exchange-first, likely Exchange-only**
- **player-stocked is the default**
- **useful farm outputs are intentionally encouraged**
- **unlimited buy-only is a narrow world-protection exception**
- **disabled protects progression**
- **stock uses capped buffers with slow background turnover**
- **sell price tapers smoothly with stock saturation**
- **buying is GUI-driven**
- **selling is command-driven**
- **prices are standardized from `worth.yml` via internal import**
- **the whole system should remain elegant, performant, and clean in code**
