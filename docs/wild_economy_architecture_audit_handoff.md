# wild_economy architecture handoff (current state)

## Purpose

This document is the current developer-facing architecture handoff for the repository after the accepted remediation slices.

It supersedes the earlier static audit framing that was written against an older uploaded zip and several now-retired issues.

The goal of this handoff is to describe:

- the current ownership model
- the canonical runtime and persistence contracts
- what has been intentionally separated
- what has already been corrected
- what still remains in scope

This document should be treated as the current architecture guide for further work unless a newer handoff explicitly replaces it.

---

## Executive summary

The project now has a much clearer domain shape than it did at the start of the architecture review.

The important current model is:

- **economy** is the durable / atomic money domain and may be shared across the network
- **exchange** is a single-server, runtime-authoritative, write-behind domain
- **store** mirrors exchange as a single-server, runtime-authoritative, write-behind domain, with immediate product actions and durable audit/entitlement persistence behind the gameplay path
- **catalogue/config** owns economic truth such as pricing inputs, policy assignment, inclusion, and related metadata
- **`layout.yml`** is authoritative only for the **GUI presentation layer** and is separate from catalogue/classification truth
- **classifier logic** in Java is intentionally weak: a small hard safety exclusion layer plus fallback suggestion logic only

The codebase is now substantially closer to that model.

The largest remaining work is no longer correctness triage. What remains is mainly:

1. broader runtime-risk test coverage
2. documentation and handoff freshness
3. optional seam cleanup where exchange services still expose live Bukkit-oriented orchestration

---

## Current architecture by domain

## Economy

### Role

Economy is the authoritative money domain.

### Contract

- mutations must remain atomic and durable on the mutation path
- economy may be shared across servers, so it cannot assume single-server runtime authority in the same way exchange/store can
- local caches are performance helpers, not the canonical truth across the network

### Implications

- balance mutations should continue to preserve strong persistence semantics
- economy bootstrap and repository wiring should continue to be treated more cautiously than exchange/store
- exchange/store should not pull economy toward a looser write-behind model just for consistency

---

## Exchange

### Role

Exchange is the curated, stock-backed gameplay trading domain.

### Contract

- single-server runtime authority
- stock is cache-first
- persistence is write-behind
- pricing truth is config/catalog driven and computed through the canonical pricing path

### Current state

The exchange domain now has:

- a cache-first stock service with write-behind persistence
- canonical pricing interpolation rather than duplicated browse math
- browse caching that materially reduces repeated GUI recomputation
- deferred GUI prewarm so stock/layout invalidations do not immediately rebuild on the hot update path

### Important ownership rule

Exchange owns economic and stock truth.
It does **not** own GUI presentation layout truth.

---

## Store

### Role

Store is the non-stock product domain for permanent unlocks and repeatable grants.

### Contract

- single-server runtime authority
- product action success is immediate and usually delegated to another plugin or process
- store-owned live state is small; the main important owned state is permanent entitlement ownership
- entitlements are runtime-authoritative with lazy per-player load and cache
- durable audit and entitlement writes happen behind the immediate success path

### Current state

The store domain now follows the intended model much more closely:

- runtime entitlement checks use a lazy-loaded cache rather than DB reads on the hot path
- successful permanent unlocks update runtime ownership immediately
- entitlements and purchase audit are persisted asynchronously
- quit/shutdown paths flush and retain dirty state appropriately
- the store domain is no longer shaped like a pseudo-transactional mini-ledger

### Important ownership rule

Store DB writes provide durability and audit. They do not define gameplay success on the immediate path.

---

## Catalogue and config system

### Role

The catalogue/config system owns economic truth.

This includes:

- item inclusion and exclusion
- policy assignment
- eco-envelope selection
- stock-profile selection
- root values and derivation anchors
- generated or curated economic metadata

### Important config ownership

The main resource files now have a clearer intended meaning:

- `eco-envelopes.yml` — multipliers for dynamic economy response within stock bands / buckets
- `stock-profiles.yml` — the stock-band or bucket sizing model
- `policy-profiles.yml` — what each policy means for player actions
- `policy-rules.yml` — how items / patterns are assigned to policies
- `root-values.yml` — economic anchors for roots / derivation heads
- `store-products.yml` — store product definitions, separate from exchange
- `layout.yml` — GUI presentation only

### Classifier role

The Java classifier is no longer intended to be a broad source of catalog truth.

Its role is now deliberately narrow:

- hard safety exclusions for obviously dangerous/admin-only/runtime-invalid items
- weak fallback suggestions where config has not explicitly curated the result

Anything softer than that should live in config ownership rather than in increasingly opinionated Java heuristics.

---

## GUI and layout

### Role

The GUI layer owns presentation.

### Contract

`layout.yml` is authoritative for the presentation structure of the GUI, but it is **not** part of catalogue truth.

The intended model is:

- catalogue answers what the item is economically
- layout answers where and how it is shown
- GUI composes those two at render/browse time

### Important separation

This means:

- layout should not decide pricing, policy, inclusion, or classification truth
- catalogue should not be dependent on layout for economic correctness
- GUI code may consume both catalogue truth and layout config, but those remain distinct inputs

### Current state

The current accepted direction is that the code should continue moving toward that separation wherever it is still blurred.

---

## Persistence and migration ownership

### Current model

Persistence ownership is now explicit by domain:

- economy migrations belong to economy
- exchange migrations belong to exchange
- store migrations belong to store

### Important correction already made

Schema-version tracking is now domain-specific rather than just prefix-specific.

This matters because exchange and store may intentionally share the same configured prefix stem while still having different migration histories. A shared prefix must not imply a shared schema-version table.

### Practical outcome

The migration system should be treated as:

- prefix-aware for physical table names
- domain-aware for migration history

---

## Bootstrap and wiring

### Current model

`ServiceRegistry` remains the composition root, but the monolithic construction path has been decomposed into ownership-based bootstrap helpers.

The accepted direction is:

- keep one orchestration root
- avoid introducing parallel registries or service locators
- split construction logic by domain / ownership

### Current bootstrap shape

The repo now has narrower bootstrap pieces for areas such as:

- migrations / infrastructure
- economy
- exchange
- store
- GUI

This was intentionally done as a structural refactor, not a behavioural redesign.

---

## What has been corrected

The following review items should now be treated as substantially addressed rather than still-open architecture defects.

### 1. Store runtime contract

Corrected toward:

- runtime-authoritative entitlements
- lazy player load with cache
- async entitlement and audit persistence
- quit/shutdown flushing behaviour

### 2. Migration ownership / stale migration issues

Corrected toward:

- explicit store migration ownership
- domain-specific schema-version tracking
- stale migration residue retired from the active path

### 3. Pricing duplication

Corrected toward:

- canonical pricing interpolation for browse and transaction paths

### 4. Classifier overreach

Corrected toward:

- hard safety exclusions in Java
- weak fallback suggestion logic only
- softer curation moved into config-driven ownership

### 5. GUI browse lag from repeated recomputation

Corrected toward:

- browse/view-model caching
- deferred prewarm after invalidation rather than rebuild on the hot update path

### 6. `ServiceRegistry` monolith risk

Substantially reduced by decomposition into ownership-based bootstrap pieces.

---

## Remaining scope

The main remaining scope is now narrower and higher level.

## 1. Broader runtime-risk test coverage

This is the most useful remaining code-focused slice.

Tests are now better than they were, but broader integration/risk coverage is still desirable around:

- bootstrap + migration wiring after the accepted refactors
- shutdown behaviour across runtime-authoritative domains
- GUI composition paths that combine catalogue truth and layout presentation
- exchange buy/sell lifecycle behaviour under the current seams

## 2. Documentation freshness

This document addresses part of that need, but the rest of the docs tree still contains older notes written before some accepted slices landed.

When future work stabilises, the surrounding docs should continue to be refreshed so the repo does not drift back into stale explanations.

## 3. Optional exchange/Bukkit seam cleanup

This remains optional rather than urgent.

Some exchange service surfaces are still orchestration-heavy and tied to live Bukkit/player/container context. That may be acceptable if explicitly acknowledged, but further cleanup remains available if clearer service boundaries are desired.

---

## Guidance for future changes

To preserve the current architecture direction, future work should follow these rules.

### 1. Do not create parallel truth systems

When adding a new feature, choose one owner for the truth:

- config/catalogue truth
- runtime domain truth
- GUI presentation truth

Avoid making two systems co-own the same decision.

### 2. Prefer composition over duplication

If pricing, stock, entitlements, or layout behaviour already has a canonical path, reuse it rather than copying its logic into a second helper.

### 3. Keep single-server and shared-network domains distinct

Do not force exchange/store to behave like economy, and do not weaken economy semantics just to make the domains look cosmetically uniform.

### 4. Keep Java heuristics weak where config should own curation

Use Java for:

- hard safety exclusions
- minimal fallback suggestion
- runtime enforcement of invariant behaviour

Use config for:

- policy curation
- category/curation decisions
- economic tuning
- presentation layout

### 5. Keep layout separate from catalogue truth

If a future change needs layout data during GUI rendering, that is expected.
If a future change needs layout data to decide economic truth, stop and re-evaluate the ownership boundary first.

---

## Recommended next work

If development continues from the current repo state, the best next slice is:

1. broader runtime-risk integration tests

After that:

2. continue documentation refresh where older docs still describe pre-remediation architecture
3. optionally revisit exchange/Bukkit seam clarity if it still feels worth the cost

---

## Final summary

The architecture is now in a much healthier state than it was when the initial audit began.

The important current truth is not that the project became more generic or more abstract. The important truth is that ownership is clearer:

- economy owns durable/shared money semantics
- exchange and store own single-server runtime state with write-behind persistence
- config/catalogue owns economic truth
- `layout.yml` owns GUI presentation only
- Java heuristics are safety/fallback tools, not the main curation engine

That is the model future work should preserve.
