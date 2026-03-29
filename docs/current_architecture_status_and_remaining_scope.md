# current architecture status and remaining scope

This document is a concise status board for the accepted architecture-review slices.

It exists so future work can quickly tell what has already been corrected and what remains in scope.

---

## Accepted / substantially addressed

### Store runtime model

Accepted direction:

- single-server runtime-authoritative store state
- lazy per-player entitlement load with cache
- immediate action execution
- async entitlement and purchase-audit persistence
- quit/shutdown flush behaviour

### Migration ownership and schema-version tracking

Accepted direction:

- domain-specific migration ownership
- domain-specific schema-version tracking
- exchange/store shared-prefix collision fixed
- stale migration residue retired from the active path

### Canonical pricing path

Accepted direction:

- browse pricing uses the canonical pricing path rather than duplicated interpolation logic

### Classifier pruning

Accepted direction:

- Java classifier retains only hard safety exclusions and weak fallback suggestion logic
- softer curation belongs in config ownership

### GUI lag from repeated browse recomputation

Accepted direction:

- browse caching in the layout-composition path
- deferred prewarm after invalidation when the last relevant GUI closes

### ServiceRegistry decomposition

Accepted direction:

- `ServiceRegistry` remains the composition root
- construction logic is decomposed into ownership-based bootstrap helpers
- no competing registry/service-locator model introduced

---

## Locked ownership model

### Domain authority

- **Economy**: atomic/durable, may be shared across the network
- **Exchange**: single-server runtime-authoritative, write-behind persistence
- **Store**: single-server runtime-authoritative, write-behind persistence

### Catalogue vs presentation

- **Catalogue/config** owns item economic truth
- **`layout.yml`** is authoritative for GUI presentation only
- catalogue truth and layout truth are separate inputs that the GUI composes

### Java heuristics

- hard safety exclusions remain in Java
- weak fallback suggestion logic remains in Java
- real curation belongs in config ownership

---

## Remaining scope

### 1. Broader runtime-risk test coverage

Still worth adding over time:

- bootstrap + migration integration coverage after the accepted refactors
- exchange buy/sell lifecycle integration tests
- shutdown/dirty-state behaviour across runtime-authoritative domains
- GUI composition tests around catalogue truth + layout presentation

### 2. Documentation freshness

The docs tree still contains older notes that describe the repo before some accepted slices landed.

The most important current docs are:

- `docs/wild_economy_architecture_audit_handoff.md`
- this file

Other older notes should be read with care until refreshed.

### 3. Optional exchange/Bukkit seam cleanup

This is no longer urgent.

It remains available if future refactoring wants exchange service seams to be more explicit about their live Bukkit/player/container coupling.

---

## What is not in current scope

These items should not be treated as open defects unless new evidence appears:

- store still behaving like a DB-authoritative mini-ledger
- store/exchange migration collision from shared prefixes
- duplicated browse vs transaction pricing math
- classifier remaining the main source of catalog policy truth
- `ServiceRegistry` still being a single monolithic construction script

Those were real issues earlier in the review, but they are no longer the main active architecture problems.
