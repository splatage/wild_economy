# wild_economy Admin Design and Specifications

Revision date: 2026-03-21  
Repo snapshot: `02c67a3f26db3817f95cbe72b3fb8cae3021b03f`  
Status: Locked Phase 1 admin/catalog design with current implementation notes  
Scope: Admin-facing catalog, classification, policy, stock-profile, and economic-envelope tooling for `wild_economy`

---

## 1. Purpose

This document defines the admin architecture and operator workflow for managing the `wild_economy` Exchange catalog.

The design goal is to make catalog and economy administration:

- rule-driven first
- override-driven second
- human-readable on disk
- safe to preview, validate, diff, publish, and roll back
- explainable item-by-item
- performant and non-invasive to player-facing runtime paths

This spec is aligned to the current plugin direction:

- Exchange-first economy
- generated catalog + override merge model
- root-value anchored item derivation
- fast player-facing runtime paths
- admin changes reviewed before going live

---

## 2. Current implementation snapshot at `02c67a3`

The repo already contains a useful backend foundation, but the full admin control plane is not complete yet.

### 2.1 Implemented today

At this snapshot, the implemented admin/catalog pieces include:

- `/shopadmin reload`
- `/shopadmin generatecatalog`
- a catalog generator facade that:
  - loads `root-values.yml`
  - builds a Bukkit recipe graph
  - derives anchored values
  - classifies items
  - suggests policy states
- generator outputs:
  - `generated/generated-catalog.yml`
  - `generated/generated-catalog-summary.yml`
- runtime catalog loading from generated base + explicit overrides
- wildcard-capable override loading through `exchange-items.yml`
- a single broad admin permission: `wild_economy.admin`

### 2.2 Not implemented yet

At this snapshot, the following Phase 1 admin/catalog work is still missing:

- `policy-rules.yml`
- `manual-overrides.yml` as a distinct file separate from the current override model
- `stock-profiles.yml`
- `eco-envelopes.yml`
- `/shopadmin catalog preview`
- `/shopadmin catalog validate`
- `/shopadmin catalog diff`
- `/shopadmin catalog apply`
- generated diff artifact
- publish/published state separation
- snapshot and rollback pipeline
- item decision trace backend as a first-class operator surface
- granular admin permission tiers

### 2.3 Important current implementation notes

At this snapshot, generated catalog output is still used directly as the runtime generated base.  
The fuller staged `generated` proposal vs `published` live-state split described below is therefore a **target design**, not current behavior.

---

## 3. Design principles

### 3.1 Files are the source of truth

Admin intent must be stored in human-readable files that can be:

- version controlled
- diffed
- reviewed
- edited by hand
- restored from backup

Any GUI added later should act as an editor, inspector, and reviewer for those files, not as an opaque second source of truth.

### 3.2 Preview before publish

Generation and admin edits must support a staging workflow:

1. generate proposal
2. review proposal
3. validate proposal
4. inspect diff
5. publish explicitly
6. roll back if necessary

No major catalog mutation should happen silently.

### 3.3 Rule-first administration

Admins should usually fix the system by changing rules, profiles, or envelopes, not by editing hundreds of items individually.

Manual item overrides remain important, but should be sparse, explicit, and easy to explain.

### 3.4 Runtime safety

Admin tooling must not compromise the fast runtime path of the player-facing exchange.

Heavy generation, diffing, validation, and report building should stay off the gameplay-critical path.

### 3.5 Explainability

For any item, the system should be able to explain:

- why it was included or excluded
- how it was derived
- which rule matched
- why it was assigned its final policy
- which stock profile and eco envelope it uses
- whether a manual override won last

---

## 4. Admin goals

Admins need control over:

1. which items belong to which policy groups
2. how generated items are classified
3. how stock behavior is tuned by reusable profiles
4. how economic guardrails are assigned by reusable envelopes
5. which generated changes are acceptable before going live
6. how to trace and review a decision for one item or one rule

---

## 5. Locked Phase 1 scope

This document is locked to **Phase 1 admin/catalog work only**.

### 5.1 Deliverables

Phase 1 must deliver:

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

### 5.2 Explicit non-deliverables for Phase 1

Do **not** scope-creep Phase 1 with the following:

- full GUI rule editor
- rollback browser UI
- stock dashboard UI
- quote simulator UI
- broad analytics dashboards
- automatic publish on generation
- free-form scripting DSL
- marketplace admin tooling

These may be revisited in later phases only after the Phase 1 file-and-command pipeline is solid.

---

## 6. Catalog policy model

The admin system should support four policy groups:

- `ALWAYS_AVAILABLE`
- `EXCHANGE`
- `SELL_ONLY`
- `DISABLED`

### 6.1 Policy meanings

#### `ALWAYS_AVAILABLE`

Used for narrow, intentionally unlimited-buy items.

Examples:

- landscape-protection materials
- nuisance-harvest materials
- special utility materials chosen by policy

These should usually be buy-enabled and not depend on live player stock.

#### `EXCHANGE`

Used for stock-backed goods.

This is the primary/default mode for whitelisted, standardized, player-supplied items.

#### `SELL_ONLY`

Used where admins want to reward collection, disposal, or cleanup without making the item part of general stock-backed redistribution.

#### `DISABLED`

Used for items that should not participate in the exchange.

Examples:

- progression-sensitive items
- unsafe/admin-only items
- spawn eggs
- command blocks
- structural/debug items
- items blocked by design intent

---

## 7. Classification model

The admin system should classify items in two dimensions.

### 7.1 GUI group

Broad player-facing top-level browsing group.

Examples:

- Farming
- Mining
- Wood
- Mob Drops
- Food
- Redstone & Utility
- Misc

This is for player navigation and should remain stable and simple.

### 7.2 Detail category

Generated/internal finer-grained classification for admin review and rule targeting.

Examples:

- logs
- planks
- leaves
- ores
- ingots
- gems
- seeds
- crops
- cooked_food
- banners
- decor_glass
- decor_stone
- rails
- containers
- mob_utility
- dyes

This supports rule targeting and subcategory review without forcing the player UI to become cluttered.

---

## 8. Rule system

The rule system must be ordered and deterministic.

### 8.1 Rule sources

#### `policy-rules.yml`

Assigns:

- policy
- GUI group
- detail category
- stock profile
- eco envelope
- notes

This is the broad rule-driven layer.

#### `manual-overrides.yml`

Explicit per-item corrections that win last.

This is for exceptions, not for replacing the whole rule system.

### 8.2 Rule matching ideas

The rule system should support practical, human-readable matching such as:

- exact item key
- namespace prefix
- wildcard or glob-style material patterns
- derived depth thresholds
- root-anchored / non-root-anchored conditions
- category matches
- group matches

### 8.3 Rule ordering philosophy

- broad rules first
- more specific rules later
- manual overrides last
- hard-disabled safety still wins where appropriate

---

## 9. Stock profiles

Stock profiles are reusable tuning profiles for item stock behavior.

### 9.1 Purpose

A stock profile should let admins change stock behavior for many items at once without repeating fields everywhere.

### 9.2 Suggested stock profile fields

Each stock profile should support:

- `target-stock`
- `soft-cap`
- `overflow-threshold`
- `low-stock-threshold`
- `dead-stock-threshold`
- `buy-visible-below`
- `restock-priority-weight`
- `turnover-window`
- `turnover-decay-rate`
- `notes`

### 9.3 Example profile names

- `farm_bulk_default`
- `ore_standard`
- `wood_bulk`
- `sell_only_cleanup`
- `rare_drop_tight`
- `unlimited_buy_utility`

---

## 10. Stock buckets

Stock buckets are derived states used for reporting and tuning.

### 10.1 Suggested stock buckets

- `EMPTY`
- `LOW`
- `HEALTHY`
- `HIGH`
- `SATURATED`
- `OVERFLOW`

### 10.2 Purpose of stock buckets

Stock buckets support:

- reports
- pricing diagnostics
- alerts
- eco-envelope behavior
- admin review

They do not need to be player-facing in Phase 1.

---

## 11. Eco envelopes

Eco envelopes are reusable guardrail profiles that constrain economic behavior.

### 11.1 Purpose

An eco envelope defines the permitted economic behavior range for an item or group of items.

This is not just one price number. It is the allowed envelope within which stock-based pricing logic operates.

### 11.2 Suggested eco envelope fields

- `base-sell-multiplier`
- `minimum-sell-multiplier`
- `low-stock-bonus-multiplier`
- `soft-cap-floor-multiplier`
- `overflow-penalty-multiplier`
- `turnover-recovery-speed`
- `max-anchor-drift`
- `allow-always-available`
- `allow-sell-only`
- `show-stock-publicly`
- `notes`

### 11.3 Example envelope names

- `farm_bulk_default`
- `ore_precious_tight`
- `world_damage_unlimited_buy`
- `sell_only_cleanup`
- `progression_sensitive_locked`

---

## 12. Phase 1 command flow

Phase 1 command work should be explicit and reviewable.

### 12.1 `/shopadmin catalog preview`

Purpose:

- generate proposal state without making it live
- write proposal outputs
- show summary counts

Expected outputs:

- generated proposal catalog
- generated summary report
- decision-trace backing data

### 12.2 `/shopadmin catalog validate`

Purpose:

- validate rules, references, categories, policies, and proposal coherence
- refuse publish if validation fails

### 12.3 `/shopadmin catalog diff`

Purpose:

- compare proposal state against current live/published state
- summarize additions, removals, and changed fields

### 12.4 `/shopadmin catalog apply`

Purpose:

- explicitly promote reviewed proposal state into live/published state
- create a snapshot for rollback
- refuse apply if validation fails

---

## 13. Generated and published files

The design should distinguish proposal artifacts from live artifacts.

### 13.1 Proposal files

Generated by preview/build steps:

- `generated/generated-catalog.yml`
- `generated/generated-catalog-summary.yml`
- `generated/generated-catalog-diff.yml`
- decision-trace output as needed for inspection and review

### 13.2 Live files

Published runtime-facing artifacts should live separately from raw proposal outputs.

Recommended direction:

- `published/published-catalog.yml`
- `published/published-summary.yml`

This separation is a **target Phase 1 design correction**. It is not the full current repo behavior yet.

### 13.3 Snapshot files

Every apply should produce a timestamped snapshot of coherent state.

Recommended snapshot contents:

- published catalog
- policy rules
- manual overrides
- stock profiles
- eco envelopes

---

## 14. Decision trace backend

Phase 1 must include an item decision trace backend.

For each item, the trace should be able to answer:

- was the item scanned as valid or rejected early?
- is it rooted or derived?
- what root value or derived value was chosen?
- what derivation depth was used?
- what rule matched?
- which policy, group, category, stock profile, and eco envelope were assigned?
- did a manual override replace any of those values?
- why was the final item included, excluded, or blocked?

This backend does not require a full GUI in Phase 1. It does require structured data the operator can inspect and report on.

---

## 15. Validation model

### 15.1 Minimum validation checks

- YAML parse success
- duplicate exact overrides
- invalid wildcard syntax
- missing stock profile reference
- missing eco envelope reference
- invalid GUI group
- invalid detail category
- invalid policy name
- conflicting forced include/exclude rules
- unresolved critical items
- blocked illegal combinations

### 15.2 Example invalid combinations

Examples of combinations the validator may reject:

- `ALWAYS_AVAILABLE` with an envelope that forbids always-available
- `SELL_ONLY` with a stock profile intended for public buying
- disabled item assigned public buy stock behavior
- missing GUI group on a live item
- missing published item key identity

---

## 16. Diff, snapshot, and rollback

### 16.1 Diff output

Generated diff should summarize:

- additions
- removals
- policy changes
- group/category changes
- stock profile changes
- eco envelope changes
- changed notes
- unresolved-to-resolved transitions
- resolved-to-unresolved regressions

### 16.2 Snapshots

Every apply should produce a timestamped snapshot of:

- live catalog
- rules
- overrides
- stock profiles
- eco envelopes

### 16.3 Rollback

Rollback should restore a full coherent state, not just one file.

A full rollback UI is **not** part of Phase 1, but snapshot compatibility should be built so rollback can be added cleanly later.

---

## 17. Permissions model

The admin tooling should support at least four permission tiers:

- `wild_economy.admin.view`
- `wild_economy.admin.catalog`
- `wild_economy.admin.economy`
- `wild_economy.admin.publish`

### 17.1 Recommended meaning

#### `view`

May inspect reports, traces, and summary data.

#### `catalog`

May edit rules, groups, and item assignments.

#### `economy`

May edit stock profiles and eco envelopes.

#### `publish`

May apply and roll back live changes.

### 17.2 Current implementation note

At repo snapshot `02c67a3`, the plugin still exposes a single broad admin permission.  
The granular permission model above is a Phase 1 target, not current completed behavior.

---

## 18. Recommended defaults

### 18.1 Publish behavior

- explicit apply only
- snapshot on every apply
- validate before apply
- refuse publish if validation fails

### 18.2 Rule philosophy

- broad rules first
- specific rules later
- manual overrides last
- explainable final decisions

### 18.3 Performance behavior

- generation and diffing should not burden normal player interactions
- validation should be operator-triggered and bounded
- runtime catalog loading should stay deterministic and fast
- do not add a large GUI/editor dependency to Phase 1

---

## 19. Later-phase ideas, explicitly deferred

These are realistic future operator features, but they are **not part of the current locked Phase 1**:

- GUI rule editor
- catalog review GUI
- publish screen UI
- stock dashboard
- quote simulator
- dead-stock review UI
- classification-confidence UI
- rollback browser UI

Keep them deferred until the file-backed command pipeline is complete and stable.

---

## 20. Summary

The recommended Phase 1 admin model for `wild_economy` is:

- files remain canonical
- rules drive most decisions
- overrides handle edge cases
- stock behavior uses reusable stock profiles
- economic constraints use reusable eco envelopes
- all major changes are previewed, validated, diffed, and explicitly applied
- every published decision is explainable
- every apply creates a rollback-capable snapshot foundation

This gives admins strong control without sacrificing clarity, safety, or maintainability.
