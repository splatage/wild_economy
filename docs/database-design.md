# wild_economy Database Design

## Status

Active design direction for runtime persistence, caching, and database access.

This document defines the current intended architecture for `wild_economy` database interaction and replaces earlier assumptions that the database would act as the live query engine for player-facing exchange actions.

The primary goal is to remove blocking database work from Bukkit main-thread player paths as quickly as possible while preserving correctness, keeping the design backend-agnostic, and supporting both SQLite and MySQL/MariaDB.

---

## 1. Goals

### Primary goals

* Remove database work from player-facing main-thread paths.
* Keep GUI interaction fast and predictable.
* Make browse, buy, sell, and turnover operate primarily on in-memory state.
* Support both SQLite and MySQL/MariaDB cleanly.
* Preserve data integrity under concurrent use.
* Keep the catalog and browse structures cheap to query at runtime.

### Secondary goals

* Reduce connection churn.
* Reduce write amplification.
* Improve scalability as catalog size and player activity grow.
* Make persistence behavior observable with metrics and logs.

---

## 2. Design principles

### 2.1 Main-thread safety first

Bukkit main-thread code must not block on JDBC for normal exchange operations.

Player-triggered actions such as:

* opening menus
* browsing categories
* viewing item details
* buying
* selling
* stock-sensitive quoting

must not depend on synchronous database round trips.

### 2.2 Runtime state should live in memory

The runtime system should treat the database as a persistence layer, not the authoritative query engine for live interaction.

The authoritative live stock state should be held in memory and updated immediately during gameplay actions.

### 2.3 Backend-specific write strategy

SQLite and MySQL/MariaDB should not be forced into the same persistence model.

* SQLite favors a single bounded writer queue.
* MySQL/MariaDB can use a small pooled async executor.

### 2.4 Immutable catalog, mutable stock

The merged exchange catalog should be loaded and held as immutable runtime data.

Live stock is mutable runtime state and should be handled separately from catalog metadata.

---

## 3. Runtime data ownership

## 3.1 Catalog

The merged catalog is loaded at startup from the generated item data plus admin override configuration.

At runtime, the catalog should be treated as immutable and used as the source of truth for:

* item definitions
* category and generated subcategory
* policy/rule state
* price model inputs
* stock capacity limits
* display metadata
* browse ordering metadata

The catalog should not be repeatedly rebuilt or queried from the database during ordinary use.

## 3.2 Live stock

Live stock should be maintained in an in-memory authoritative cache loaded at startup.

This cache should be the runtime source of truth for:

* current visible stock
* stock-sensitive browse visibility
* stock-sensitive buy eligibility
* sell room checks
* turnover adjustments
* stock-sensitive sell pricing inputs

The database should persist stock, but should not be queried synchronously for normal stock reads during runtime.

## 3.3 Transaction log

Transaction logs are persistence/audit data, not gameplay-critical read-path data.

They should not sit on the latency path for menu interaction or buy/sell completion.

---

## 4. Main-thread rules

The following operations must be safe for main-thread use:

* reading catalog entries
* reading category and subcategory browse indexes
* reading live stock from memory
* mutating live stock in memory
* performing Bukkit inventory operations
* performing Bukkit GUI operations
* performing Vault economy operations
* deciding whether a transaction is accepted or rejected

The following operations should not run synchronously on the main thread in normal runtime flow:

* JDBC reads for stock
* JDBC writes for stock
* per-item database lookups during browse
* transaction log inserts
* full turnover persistence sweeps
* repeated connection creation

---

## 5. Stock cache design

## 5.1 Role

The stock cache is the runtime authoritative state for exchange stock.

Browse, buy, sell, and turnover should use this cache rather than querying the database on demand.

## 5.2 Lifecycle

### Startup

* Load all existing stock rows from the configured backend into memory.
* Missing rows may be treated as zero stock in memory.
* The startup load should complete before the exchange is considered live.

### Runtime

* Main-thread gameplay actions read stock from memory.
* Main-thread gameplay actions mutate stock in memory immediately.
* Persistence work is enqueued asynchronously after mutation.

### Shutdown

* Dirty stock state should be flushed before plugin shutdown completes.
* Shutdown behavior should attempt a clean final persistence pass.

## 5.3 Data shape

The exact Java type is implementation-specific, but conceptually each stock entry should include at least:

* item key
* current stock count
* last updated timestamp or equivalent
* dirty state and/or version marker as needed for flush logic

## 5.4 Missing rows

Read paths must not call `ensureRowExists()`.

Missing stock rows should be handled by runtime defaults such as zero stock, with rows created only when persistence actually needs to write them.

This avoids turning reads into writes and avoids repeated write pressure during browse.

---

## 6. Browse/read-path design

## 6.1 Desired runtime behavior

Browse and detail views should be assembled from:

* immutable in-memory catalog data
* precomputed in-memory browse indexes
* live in-memory stock state

No per-item database reads should occur during normal browse flow.

## 6.2 Precomputed indexes

The plugin should precompute browse indexes at startup/reload, including:

* top-level category -> item list
* top-level category + generated subcategory -> item list
* any stable display-order views needed for paging

This allows menu building to be a lightweight filter/paginate/render step rather than repeated catalog scanning plus database access.

## 6.3 Sorting and paging

Where possible, stable ordering should be precomputed once.

Paging should operate on already-available in-memory lists rather than rebuilding expensive intermediate structures on each click.

---

## 7. Buy/sell/write-path design

## 7.1 Core rule

Gameplay acceptance and stock mutation should happen against in-memory state first.

Persistence should follow asynchronously.

## 7.2 Buy flow

Conceptual order:

1. Read catalog entry from memory.
2. Read current stock from memory.
3. Validate that purchase is allowed.
4. Validate player balance.
5. Apply gameplay/economy action.
6. Mutate in-memory stock immediately.
7. Enqueue async stock persistence.
8. Enqueue async transaction log write.

## 7.3 Sell flow

Conceptual order:

1. Read catalog entry from memory.
2. Read current stock from memory.
3. Validate sell eligibility and available room.
4. Compute stock-sensitive sale result from in-memory stock.
5. Apply gameplay/economy action.
6. Mutate in-memory stock immediately.
7. Enqueue async stock persistence.
8. Enqueue async transaction log write.

## 7.4 Main-thread boundary

Anything involving Bukkit inventories, players, items, menus, or Vault should remain on the main thread.

Anything involving JDBC persistence should occur after the gameplay decision and should be offloaded asynchronously.

---

## 8. Persistence strategy by backend

## 8.1 SQLite

SQLite should use a single bounded async writer queue.

Rationale:

* SQLite is not well served by many concurrent writers.
* A single writer reduces lock contention and simplifies ordering.
* Bounded queue size avoids unbounded memory growth during load spikes.

Expected behavior:

* Main thread enqueues stock persistence tasks and log tasks.
* Writer thread drains queue in order.
* Warnings/metrics should surface if backlog grows too large.

## 8.2 MySQL/MariaDB

MySQL/MariaDB should use a small pooled async executor.

Rationale:

* Server-based databases can handle limited concurrency better than SQLite.
* A small pool allows persistence and logging work to proceed efficiently without over-parallelizing.

Expected behavior:

* Main thread enqueues work.
* Async executor performs persistence using pooled connections.
* Pool sizing should remain intentionally small and operationally simple.

---

## 9. Connection pooling

## 9.1 Requirement

Per-call `DriverManager.getConnection(...)` use should be replaced with a real pooled connection provider.

Standard choice: HikariCP.

## 9.2 Why

Connection pooling provides:

* connection reuse
* lower connection acquisition overhead
* better operational control
* cleaner health and timeout settings
* alignment with configurable pool sizing

## 9.3 Scope

Connection pooling should be used for:

* startup preload
* async stock persistence
* async transaction log writes
* turnover persistence
* future admin/reporting queries

Even after stock caching removes most runtime reads, pooled access remains the correct persistence foundation.

---

## 10. Transaction logging

## 10.1 Role

Transaction logging is important for auditability and admin visibility, but should not block the gameplay path.

## 10.2 Design

Transaction logs should be queued and flushed asynchronously.

Prefer batching rather than issuing one insert per completed player action when possible.

## 10.3 Event sources

At minimum, the logging system should support:

* buy events
* sell events
* turnover events
* future admin adjustment events if added later

## 10.4 Delivery model

The runtime may accept a tiny delay between gameplay completion and durable log write.

That tradeoff is acceptable because it removes avoidable latency from the player path.

---

## 11. Turnover execution model

Turnover should operate on the in-memory stock map, not by synchronously querying the database item by item.

Conceptual flow:

1. Scheduled turnover task runs.
2. Reads catalog and stock from memory.
3. Computes turnover effects in memory.
4. Mutates in-memory stock.
5. Enqueues async stock persistence.
6. Enqueues async turnover log records.

This keeps turnover aligned with the same runtime ownership model as buy and sell.

A synchronous full-catalog JDBC sweep on the main thread is explicitly not the desired design.

---

## 12. Explicit anti-patterns to remove

The following patterns are now considered undesirable and should be removed from the implementation:

* Per-item JDBC reads during browse.
* Synchronous stock reads on menu click paths.
* Repeated connection creation via `DriverManager` for normal operations.
* `ensureRowExists()` on stock read paths.
* Using the database as the live source of truth for stock during runtime.
* Synchronous transaction log inserts on player actions.
* Synchronous full-catalog turnover persistence on the main thread.
* Treating SQLite and MySQL as if they should use identical write concurrency behavior.

---

## 13. Failure handling and consistency

## 13.1 General model

The runtime system should prioritize smooth gameplay while making persistence failures visible and recoverable.

Because live stock is authoritative at runtime, persistence failures do not need to block gameplay immediately, but they must be surfaced clearly.

## 13.2 Expectations

The implementation should aim to provide:

* bounded async queues
* warning/error logging on persistence backlog or failures
* retry behavior where appropriate
* clean shutdown flush attempts
* periodic flushes or snapshots for dirty state

## 13.3 Integrity direction

Longer-term, write-path persistence should move toward atomic SQL mutation where appropriate rather than read-modify-write sequences.

This is part of the later hardening phase rather than the first urgent off-main-thread phase.

---

## 14. Implementation phases

## P0 — immediate performance and architecture correction

Priority: highest.

Deliverables:

* Add live stock cache loaded at startup.
* Route stock reads to cache.
* Mutate cache first, then enqueue async persistence.
* Move transaction log writes to async queue/batching.
* Replace ad hoc JDBC connection creation with Hikari-backed provider.

Expected result:

* Database work removed from the hottest player-facing runtime paths.
* Major reduction in browse and buy/sell latency risk.

## P1 — browse/index optimization

Deliverables:

* Precompute category indexes.
* Precompute category + generated subcategory indexes.
* Pre-sort catalog entries once.
* Build browse pages from cached indexes plus live stock cache only.

Expected result:

* Very cheap category browsing.
* Stable GUI responsiveness as catalog size grows.

## P2 — write-path hardening and observability

Deliverables:

* Atomic SQL stock mutation where appropriate.
* Batched database flushes.
* Periodic snapshot flush.
* Clean shutdown flush.
* Metrics for queue depth, dirty item count, flush latency, and related health signals.

Expected result:

* Stronger persistence integrity.
* Better operational visibility.
* Reduced write amplification.

---

## 15. Current architectural position

The current intended architecture is:

* **Catalog**: immutable and fully in memory.
* **Browse indexes**: precomputed and in memory.
* **Live stock**: authoritative in-memory mutable cache.
* **Main-thread gameplay actions**: read and mutate memory only.
* **Database**: async persistence and audit/log storage.
* **SQLite**: single bounded writer queue.
* **MySQL/MariaDB**: small pooled async executor.
* **Connection management**: HikariCP.

This is the locked persistence and performance direction for the next implementation phase of `wild_economy`.
