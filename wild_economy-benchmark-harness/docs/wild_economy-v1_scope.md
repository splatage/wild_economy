# The Wild Exchange Plugin — Locked Scope v1

Revision date: 2026-03-21  
Repo snapshot: `02c67a3f26db3817f95cbe72b3fb8cae3021b03f`

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

- reward useful farms and real production
- let players buy practical bulk goods from real stock
- keep buying simple and visual through GUI
- keep selling fast and low-friction through commands
- shape item circulation intentionally through policy
- keep the codebase clean, modular, and performant
- generate a scalable item catalog from anchored roots plus explicit overrides
- keep admin/catalog control file-driven and reviewable

### Success criteria

- players immediately understand what the Exchange is for
- selling useful bulk goods feels rewarding and quick
- buying stocked goods feels easy and predictable
- the Exchange does not become a progression shortcut machine
- the system performs well on live servers
- the code stays cleanly separated by domain
- admins can review catalog changes before they go live

---

## 4. Locked v1 mechanics

### 4.1 Item policy model

Exchange behavior is driven by **item policy**.

#### A. Player-stocked

This is the **default and primary mode**.

Used for intentionally whitelisted, standardized bulk goods, especially useful farm outputs and practical server-useful materials.

Behavior:

- players can sell these items to the Exchange
- players are paid immediately
- sold items become Exchange stock
- other players can buy from that stock
- stock can rise above the soft cap
- oversupply reduces sell reward instead of hard-blocking sells

#### B. Unlimited buy

This is a **narrow exception**.

Used for selected materials where server-provided supply is preferable to destructive harvesting or ugly terrain damage.

Intended behavior:

- players may buy these items
- these items are not part of the normal stock-backed sell loop
- supply is provided by policy rather than player stock

#### C. Disabled

Items that should not participate in the Exchange.

Examples include:

- progression-sensitive items
- items that would shortcut gameplay
- admin/debug/unsafe items
- items excluded by design intent

### 4.2 Catalog generation model

The v1 catalog direction is:

- anchor root/basic item values in `root-values.yml`
- build recipe relationships from Bukkit/Paper data
- derive candidate values for non-root items from anchored roots
- generate a default catalog proposal
- merge explicit overrides last
- use the merged internal catalog at runtime

This replaces the older description that centered the system around direct runtime import from Essentials `worth.yml`.

### 4.3 Generated base plus override merge

The locked runtime/admin direction is:

- **generated base catalog** for scalable defaults
- **explicit overrides** for corrections and exceptions
- **human-readable files** as the source of admin intent
- **review before publish** as the desired admin workflow

### 4.4 Buying and selling

Buying remains **GUI-first**.

Selling remains **command-first**.

Primary selling flows:

- `/shop sellhand`
- `/shop sellall`
- `/shop sellcontainer`

This remains deliberate because selling is frequent and should stay fast.

### 4.5 Stock and pricing

Player-stocked items use:

- live tracked stock
- a soft stock cap as a pricing anchor
- turnover/drain over time
- stock-sensitive sell taper
- atomic buy-side stock consumption

Core rules:

- soft caps do not hard-block sells
- buy-side stock consumption must be correct and atomic
- sell pricing should be computed by grouped item key, not noisy per-slot logic
- database persistence and transaction logging should stay off the hot interaction path

---

## 5. Locked admin/catalog scope for current work

The current locked admin/catalog slice is **Phase 1 only**.

### Phase 1 deliverables

1. `policy-rules.yml`
2. `manual-overrides.yml`
3. `stock-profiles.yml`
4. `eco-envelopes.yml`
5. `/shopadmin catalog preview`
6. `/shopadmin catalog validate`
7. `/shopadmin catalog diff`
8. `/shopadmin catalog apply`
9. generated summary + diff reports
10. item decision trace backend

### Explicitly deferred

The following are **not** part of the current locked Phase 1 implementation slice:

- full GUI rule editor
- rollback browser UI
- stock dashboard UI
- quote simulator UI
- broad analytics suites
- automatic publish on generation
- free-form scripting DSLs

---

## 6. Locked architectural boundaries

### Must remain true

- no DB I/O on main-thread click paths
- GUI/presentation must stay separate from business logic and persistence
- item normalization is strict and first-class
- runtime stock mutation stays in memory on the gameplay path
- persistence and transaction logging happen asynchronously
- admin/catalog generation remains deterministic and explainable
- file-backed admin intent remains reviewable and versionable

### Current source-of-truth direction

For the current repo direction, source-of-truth is split as follows:

- `root-values.yml` anchors value roots
- generated catalog output supplies the scalable base catalog
- explicit admin overrides win last
- runtime consumes the merged internal catalog

---

## 7. Non-goals for v1

The following remain out of scope for core v1:

- Marketplace as a core public system
- auctions or bids
- negotiation
- dynamic live-market buy pricing
- broad fuzzy matching
- public works integration
- “sell everything by default” behavior
- turning the Exchange into a progression bypass
- large admin dashboards as a required v1 dependency

---

## 8. Summary

The locked v1 shape is now:

- a curated Exchange economy
- player-stocked trade as the default path
- unlimited-buy as a narrow exception
- disabled items protecting progression and server identity
- generated catalog base from anchored roots and recipe derivation
- explicit override merge for admin control
- GUI-first buying and command-first selling
- performant runtime paths with asynchronous persistence
- a file-driven, reviewable admin/catalog pipeline built in staged phases
