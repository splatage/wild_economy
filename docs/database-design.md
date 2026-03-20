# wild_economy Database Design

## Status

Active design direction for runtime persistence, caching, and database access.

This document defines the current intended architecture for `wild_economy` database interaction and replaces earlier assumptions that the database would act as the live query engine for player-facing exchange actions.

The primary goal is to remove blocking database work from Bukkit main-thread player paths as quickly as possible while preserving correctness, keeping the design backend-agnostic, and supporting both SQLite and MySQL/MariaDB.

---

## 1. Goals

### Primary goals

- Remove database work from player-facing main-thread paths.
- Keep GUI interaction fast and predictable.
- Make browse, buy, sell, and turnover operate primarily on in-memory state.
- Support both SQLite and MySQL/MariaDB cleanly.
- Preserve data integrity under concurrent use.
- Keep the catalog and browse structures cheap to query at runtime.

### Secondary goals

- Reduce connection churn.
- Reduce write amplification.
- Improve scalability as catalog size and player activity grow.
- Make persistence behavior observable with metrics and logs.

---

## 2. Design principles

### 2.1 Main-thread safety first

Bukkit main-thread code must not block on JDBC for normal exchange operations.

Player-triggered actions such as:

- opening menus
- browsing categories
- viewing item details
- buying
- selling
- stock-sensitive quoting

must not depend on synchronous database round trips.

### 2.2 Runtime state should live in memory

The runtime system should treat the database as a persistence layer, not the authoritative query engine for live interaction.

The authoritative live stock state should be held in memory and updated immediately during gameplay actions.

### 2.3 Backend-specific write strategy

SQLite and MySQL/MariaDB should not be forced into the same persistence model.

- SQLite favors a single bounded writer queue.
- MySQL/MariaDB can use a small pooled async executor.

### 2.4 Immutable catalog, mutable stock

The merged exchange catalog should be loaded and held as immutable runtime data.

Live stock is mutable runtime state and should be handled separately from catalog metadata.

---

## 3. Runtime data ownership

## 3.1 Catalog

The merged catalog is loaded at startup from the generated item data plus admin override configuration.

At runtime, the catalog should be treated as immutable and used as the source of truth for:

- item definitions
- category and generated subcategory
- policy/rule state
- price model inputs
- stock-cap anchors
- display metadata
- browse ordering metadata

The catalog should not be repeatedly rebuilt or queried from the database during ordinary use.

## 3.2 Live stock

Live stock should be maintained in an in-memory authoritative cache loaded at startup.

This cache should be the runtime source of truth for:

- current visible stock
- stock-sensitive browse visibility
- stock-sensitive buy eligibility
- turnover adjustments
- stock-sensitive sell pricing inputs
- buy-side stock consumption correctness

The database should persist stock, but should not be queried synchronously for normal stock reads during runtime.

## 3.3 Transaction log

Transaction logs are persistence/audit data, not gameplay-critical read-path data.

They should not sit on the latency path for menu interaction or buy/sell completion.

---

## 4. Main-thread rules

The following operations must be safe for main-thread use:

- reading catalog entries
- reading category and subcategory browse indexes
- reading live stock from memory
- mutating live stock in memory
- performing Bukkit inventory operations
- performing Bukkit GUI operations
- performing Vault economy operations
- deciding whether a transaction is accepted or rejected
- pricing one sell batch per item key

The following operations should not run synchronously on the main thread in normal runtime flow:

- JDBC reads for stock
- JDBC writes for stock
- per-item database lookups during browse
- transaction log inserts
- full turnover persistence sweeps
- repeated connection creation

---

## 5. Stock cache design

## 5.1 Role

The stock cache is the runtime authoritative state for exchange stock.
Browse, buy, sell, and turnover should use this cache rather than querying the database on demand.

## 5.2 Lifecycle

### Startup

- Load all existing stock rows from the configured backend into memory.
- Missing rows may be treated as zero stock in memory.
- The startup load should complete before the exchange is considered live.

### Runtime

- Main-thread gameplay actions read stock from memory.
- Main-thread gameplay actions mutate stock in memory immediately.
- Buy-side player-stocked consumes must be atomic in memory.
- Persistence work is enqueued asynchronously after mutation.

### Shutdown

- Dirty stock state should be flushed before plugin shutdown completes.
- Shutdown behavior should attempt a clean final persistence pass.

## 5.3 Data shape

The exact Java type is implementation-specific, but conceptually each stock entry should include at least:

- item key
- current stock count
- last updated timestamp or equivalent
- dirty state and/or version marker as needed for flush logic

## 5.4 Missing rows

Read paths must not call `ensureRowExists()`.
Missing stock rows should be handled by runtime defaults such as zero stock, with rows created only when persistence actually needs to write them.

This avoids turning reads into writes and avoids repeated write pressure during browse.

---

## 6. Browse/read-path design

## 6.1 Desired runtime behavior

Browse and detail views should be assembled from:

- immutable in-memory catalog data
- precomputed in-memory browse indexes
- live in-memory stock state

No per-item database reads should occur during normal browse flow.

## 6.2 Stock display

For player-facing views, stock state should come from the in-memory stock snapshot and catalog metadata.

The database is not part of the hot browse path.

---

## 7. Write-path design

## 7.1 Buy flow

For `PLAYER_STOCKED` items, the correct conceptual order is:

1. Read catalog entry from memory.
2. Read stock snapshot from memory for quoting/display.
3. Validate that the purchase is allowed.
4. Validate player balance and inventory space.
5. Atomically consume the requested stock amount in memory.
6. Apply economy and gameplay action.
7. If the buy cannot complete after stock consume, restore the consumed stock.
8. Enqueue async stock persistence.
9. Enqueue async transaction log write.

### Important constraint

Do **not** use this unsafe pattern:

- read snapshot
- confirm stock exists
- perform economy/item delivery
- decrement stock later in a separate step

That pattern allows overselling under concurrent buys.

## 7.2 Sell flow

For player-triggered sells, the correct conceptual order is:

1. Read catalog entry from memory.
2. Scan inventory/container contents.
3. Normalize and validate items.
4. Aggregate quantities by item key.
5. Read current stock snapshot once per sold item key.
6. Compute one batch quote per item key.
7. Apply gameplay/economy action.
8. Mutate in-memory stock immediately.
9. Enqueue async stock persistence.
10. Enqueue async transaction log write.

### Important interpretation

Soft stock caps do **not** require sell-room checks.

The stock cap is a pricing anchor:

- below cap, sell value tapers downward as stock rises
- at or above cap, sell value floors at the configured minimum
- sells are not rejected purely because stock is already high

## 7.3 Turnover flow

Turnover should:

- read current live stock from memory
- remove stock in memory
- persist later asynchronously
- log the actual removed amount, not merely the requested amount

---

## 8. Async persistence strategy

## 8.1 Stock persistence

Stock writes should be decoupled from the gameplay path.

Desired behavior:

- live stock mutates immediately in memory
- changed keys are marked dirty
- dirty keys are flushed asynchronously in batches
- shutdown performs a best-effort final synchronous flush

## 8.2 Transaction logging

Transaction logs should be written asynchronously.

They are important for audit/support, but should not extend the latency of player-facing actions.

## 8.3 Backend notes

### SQLite

- prefer a single bounded writer queue
- avoid write storms from chatty per-slot logging
- benefit from grouped-by-item-key sell planning and batched flushes

### MySQL/MariaDB

- use pooled connections
- use a small async executor for stock/log persistence
- preserve the same memory-first gameplay path semantics as SQLite

---

## 9. Performance direction

The highest-value performance principles are:

- keep pricing math on the gameplay path because it is cheap
- remove JDBC from gameplay paths because it is expensive
- aggregate sell actions by item key before pricing and logging
- mutate stock once per sold item key rather than once per slot
- keep persistence and transaction logging asynchronous

### Practical implication

The important optimization is not “make pricing async.”
The important optimization is:

- inventory scan -> grouped totals by item key -> one quote per key -> one stock mutation per key -> async persistence/logging afterward

---

## 10. Anti-patterns to avoid

Avoid the following:

- synchronous stock reads from the database during browse or buy/sell
- `ensureRowExists()` on read paths
- per-slot sell pricing when one aggregated item-key quote would do
- buy flows that validate stock from a snapshot and decrement stock later in a non-atomic step
- treating stock caps as hard sell ceilings when the intended design is soft-cap pricing
- putting transaction log writes on the gameplay latency path

---

## 11. Integrity priorities

The current hardening order should be:

1. keep authoritative live stock in memory
2. ensure atomic buy-side consumption correctness
3. keep sell pricing and stock mutation clean and grouped by item key
4. keep DB persistence and transaction logging asynchronous
5. add observability and metrics around persistence behavior

This preserves the intended v1 balance between correctness, simplicity, and performance.
