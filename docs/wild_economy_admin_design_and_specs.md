# wild_economy Admin Design and Specifications

Revision date: 2026-03-22  
Repo snapshot: `3d73036e5d764b26f96b8b36738dc8fecb9a8724`  
Status: Locked Phase 1 admin/catalog design with current implementation notes  
Scope: Admin-facing catalog, classification, policy, stock-profile, and economic-envelope tooling for `wild_economy`

---

## 1. Purpose

This document defines the admin architecture and operator workflow for managing the `wild_economy` Exchange catalog.

The design goal is to make catalog and economy administration:

- rule-driven first
- override-driven second
- human-readable on disk
- safe to preview, validate, diff, publish, and recover
- explainable item-by-item
- performant and non-invasive to player-facing runtime paths

This document is aligned to the current plugin direction:

- Exchange-first economy
- admin/build generation from rooted values, rules, profiles, envelopes, and overrides
- published live runtime catalog at `exchange-items.yml`
- fast player-facing runtime paths that consume only the published catalog
- admin changes reviewed before going live

---

## 2. Current implementation snapshot at `3d73036`

The repo now contains a substantial Phase 1 admin/catalog foundation. This is no longer just a future design target.

### 2.1 Implemented today

At this snapshot, the implemented admin/catalog pieces include:

- `/shopadmin reload`
- `/shopadmin`
- `/shopadmin gui`
- `/shopadmin generatecatalog`
- `/shopadmin catalog preview`
- `/shopadmin catalog validate`
- `/shopadmin catalog diff`
- `/shopadmin catalog apply`
- `/shopadmin item <item_key>`

The admin backend includes:

- catalog generation from Bukkit/Paper material and recipe data
- rule-driven policy/category/profile/envelope assignment
- generated reports written under `generated/`
- live catalog publish to `exchange-items.yml`
- item decision traces
- review bucket reports
- rule impact reports
- manual overrides stored in `manual-overrides.yml`
- admin GUI flows for review, inspection, and manual override editing

The admin config surface now includes these managed files:

- `policy-rules.yml`
- `policy-profiles.yml`
- `manual-overrides.yml`
- `stock-profiles.yml`
- `eco-envelopes.yml`

### 2.2 Managed config integrity model

Required managed config files are not supposed to be invented ad hoc inside individual loaders.

The current desired model is:

- bootstrap/reload performs centralized managed-file materialization
- missing required managed files are regenerated from bundled defaults
- regeneration emits a clear warning and admin next-step guidance
- loaders are responsible for reading and validation, not file creation
- admin commands and GUI screens consume config only; they are not config authors

This keeps configuration recovery graceful without scattering hidden side effects through admin code paths.

### 2.3 Current implementation note: permission model

At this snapshot the admin permission model is still effectively broad and simple.

Current state:

- `/shopadmin` is treated as an op/admin capability
- the command and GUI surface are intended for trusted operators

Planned improvement:

- split admin permissions into narrower capabilities such as view, reload, apply, and override once the current command/router structure is cleaned up

This permission split is desirable, but it should follow a cleaner command architecture rather than being layered onto monolithic handlers indefinitely.

---

## 3. Design principles

### 3.1 Files are the source of truth

Admin intent must be stored in human-readable files that can be:

- version controlled
- diffed
- reviewed
- edited by hand
- restored from backup

Any GUI acts as an editor, inspector, and reviewer for those files. It must not become an opaque second source of truth.

### 3.2 Preview before publish

Generation and admin edits must support a staging workflow:

1. generate proposal
2. review proposal
3. validate proposal
4. inspect diff
5. publish explicitly
6. reload/apply explicitly

No major catalog mutation should happen silently.

### 3.3 Rule-first administration

Admins should usually fix the system by changing rules, profiles, or envelopes, not by editing hundreds of items individually.

Manual item overrides remain important, but should be sparse, explicit, and easy to explain.

### 3.4 Runtime safety

Admin tooling must not compromise the fast runtime path of the player-facing exchange.

Heavy generation, diffing, validation, and report building should stay off the gameplay-critical path wherever practical.

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
7. how to recover cleanly from missing managed config files

---

## 5. Locked Phase 1 scope

This document is locked to **Phase 1 admin/catalog work only**.

### 5.1 Deliverables

Phase 1 includes:

1. `policy-rules.yml`
2. `policy-profiles.yml`
3. `manual-overrides.yml`
4. `stock-profiles.yml`
5. `eco-envelopes.yml`
6. `/shopadmin catalog preview`
7. `/shopadmin catalog validate`
8. `/shopadmin catalog diff`
9. `/shopadmin catalog apply`
10. generated summary + diff + validation reports
11. item decision trace backend
12. review bucket reporting
13. rule impact reporting
14. GUI inspection and manual override editing

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

These may be revisited later only after the Phase 1 file-and-command pipeline is solid.

---

## 6. Catalog policy model

The admin system supports four policy groups:

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

These are usually buy-enabled and do not depend on live player stock.

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

The admin system classifies items in two dimensions.

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

#### `policy-profiles.yml`

Defines reusable policy behavior profiles that bridge the admin-facing catalog policy model and the runtime exchange behavior.

Typical fields include:

- runtime policy mapping
- buy enabled
- sell enabled
- stock-backed
- unlimited-buy
- requires-player-stock-to-buy
- default stock profile
- default eco envelope
- description

#### `manual-overrides.yml`

Explicit per-item corrections that win last.

This is for exceptions, not for replacing the whole rule system.

### 8.2 Rule ordering philosophy

- broad rules first
- more specific rules later
- manual overrides last
- hard-disabled safety still wins where appropriate

---

## 9. Stock profiles

Stock profiles are reusable tuning profiles for item stock behavior.

### 9.1 Purpose

A stock profile should let admins change stock behavior for many items at once without repeating fields everywhere.

### 9.2 Current stock profile fields

The current Phase 1 admin stock-profile contract is centered on reusable publish-time stock tuning.

The current bundled/admin loader contract is:

- `stock-cap`
- `turnover-amount-per-interval`
- `low-stock-threshold`
- `overflow-threshold`

These are resolved by the admin pipeline into the published runtime `exchange-items.yml`.

This document does not define additional stock-profile fields beyond the current implemented contract.

---

## 10. Eco envelopes

Eco envelopes are reusable guardrail profiles that constrain economic behavior.

### 10.1 Purpose

An eco envelope should make it easy to reuse consistent economic guardrails across many items.

### 10.2 Current eco-envelope fields

The current Phase 1 admin eco-envelope contract resolves reusable pricing behavior into per-item runtime endpoint prices.

The current bundled/admin loader contract is:

- `buy-price-at-min-stock-multiplier`
- `buy-price-at-max-stock-multiplier`
- `sell-price-at-min-stock-multiplier`
- `sell-price-at-max-stock-multiplier`

These are admin/build inputs only. Runtime `/shop` does not consume named eco-envelope references.

---

## 11. Admin command and artifact flow

### 11.1 Command intent

- `reload` refreshes managed config and plugin runtime state
- `preview` builds the staged/generated view without publishing
- `validate` reports problems
- `diff` compares staged output against the current live catalog
- `apply` publishes the generated live catalog and reloads runtime state
- `item` explains one item’s generated decision path

### 11.2 Generated outputs

The admin pipeline writes report artifacts under `generated/`, including outputs such as:

- generated catalog
- summary
- validation report
- diff report
- item decision traces
- rule impacts
- review buckets

### 11.3 Live output

Publishing writes the live catalog to `exchange-items.yml`.

That file is the canonical published runtime catalog consumed by `/shop`.

Runtime does not consume:

- `root-values.yml`
- `stock-profiles.yml`
- `eco-envelopes.yml`
- generated review artifacts

Those remain admin/build inputs and reports only.

### 11.4 Snapshot behavior

Apply may create a snapshot of the prior live state before publishing the new catalog.

---

## 12. Admin GUI surface

The current admin GUI is intended to support review rather than replace the file model.

Current GUI role:

- open root review view
- browse review buckets
- browse rule impacts
- inspect one item decision
- edit/remove manual overrides
- stage and confirm apply

The GUI is intentionally an inspection/editor layer over files and generated reports, not a standalone source of truth.

---

## 13. Managed config recovery contract

Missing managed files should be handled centrally during bootstrap/reload.

Desired behavior:

- if a required managed file is missing, regenerate it from bundled defaults
- log a prominent warning
- include admin next-step guidance in the warning
- continue with a valid, reviewable default file on disk

Example guidance style:

- file regenerated from defaults
- review the regenerated file before catalog changes
- run `/shopadmin catalog validate`
- reload or restart after review if required

This central materialization pass is preferred over silent self-healing in individual loaders.

---

## 14. Current architectural debt

The admin system is now functionally useful, but the code shape is not yet as elegant as it should be.

The main debt areas are:

- `ShopAdminCommand` carries command parsing, permission checks, orchestration, and summary rendering together
- `AdminMenuRouter` carries navigation, state building, apply flow, permission gates, and some recovery behavior together
- generated report loading is too close to GUI navigation concerns
- command and GUI paths duplicate parts of the same admin action workflow

This does not block Phase 1 use, but it is the next good cleanup slice.

---

## 15. Next structural goal after Phase 1 hardening

The next major improvement should be to make the admin code less monolithic by separating:

- command dispatch
- permission policy
- catalog action orchestration
- summary/report formatting
- GUI navigation
- generated-report loading
- manual-override application services

That refactor should preserve behavior and file formats while improving readability, testability, and future extension.

---

## 16. Summary

The admin/catalog system is now a real Phase 1 control plane, not just a future design.

What is already true:

- files are central
- preview/validate/diff/apply exists
- generated artifacts exist
- manual overrides exist
- GUI review exists
- managed config recovery is intended to be central, not loader-local
- runtime `/shop` is intended to consume only the published `exchange-items.yml`
- admin source abstractions remain on the admin/build side

What still needs improvement:

- narrower admin permissions
- cleaner decomposition of command/router code
- better separation between orchestration and presentation
- eventual rollback/admin UX improvements in later phases

