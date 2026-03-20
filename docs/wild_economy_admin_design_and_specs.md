# wild_economy Admin Design and Specifications

Revision date: 2026-03-21  
Status: Proposed design spec  
Scope: Admin-facing catalog, classification, policy, stock-profile, and economic-envelope tooling for `wild_economy`

---

## 1. Purpose

This document defines the intended admin architecture and operator workflow for managing the `wild_economy` Exchange catalog.

The design goal is to make catalog and economy administration:

- rule-driven first
- override-driven second
- GUI-assisted
- human-readable on disk
- safe to preview, validate, publish, and roll back

This spec is intentionally aligned to the current plugin direction:

- Exchange-first economy
- generated catalog + override merge model
- root-value anchored item derivation
- distinct policy states:
  - `ALWAYS_AVAILABLE`
  - `EXCHANGE`
  - `SELL_ONLY`
  - `DISABLED`
- fast player-facing runtime paths
- admin changes reviewed before going live

---

## 2. Design Principles

### 2.1 Files are the source of truth

Admin intent must be stored in human-readable files that can be:

- version controlled
- diffed
- reviewed
- edited by hand
- restored from backup

The GUI should act as an editor, inspector, and review surface for those files, not as an opaque second source of truth.

### 2.2 Preview before publish

Generation and admin edits must support a staging workflow:

1. generate proposal
2. review proposal
3. validate proposal
4. publish explicitly
5. roll back if necessary

No major catalog mutation should happen silently.

### 2.3 Rule-first administration

Admins should usually fix the system by changing rules, profiles, or envelopes, not by editing hundreds of items individually.

Manual item overrides remain important, but should be sparse and explicit.

### 2.4 Runtime safety

Admin tooling must not compromise the fast runtime path of the player-facing exchange.

Heavy generation, diffing, validation, and report building should stay off the gameplay-critical path.

### 2.5 Explainability

For any item, the system should be able to explain:

- why it was included or excluded
- how it was derived
- which rule matched
- why it was assigned its final policy
- which stock profile and eco envelope it uses

---

## 3. Admin Goals

Admins need control over:

1. which items belong to which policy groups
2. how generated items are classified
3. which groups of items are always available, stock-backed, sell-only, or disabled
4. which stock profile an item uses
5. which economic guardrails apply to an item
6. how proposed catalog changes are reviewed before going live
7. how changes are published, backed up, and rolled back

---

## 4. Core Admin Workflow

### 4.1 Ideal operator journey

#### Step 1: Generate a proposal

Command:

```text
/shopadmin catalog preview
```

This builds a proposal from:

- root values
- recipe graph
- derivation rules
- category rules
- policy rules
- stock profile rules
- eco envelope rules
- manual overrides

But it does **not** publish anything live.

#### Step 2: Review the proposal

Admin uses GUI and/or generated report files to review:

- newly included items
- newly excluded items
- policy changes
- stock profile changes
- eco envelope changes
- unresolved items
- items blocked by depth limit
- items with low-confidence classification
- items forced by manual override

#### Step 3: Adjust rules first

Admin should usually resolve bulk issues through:

- policy rules
- category rules
- stock profile rules
- eco envelope rules

Only special cases should go into manual overrides.

#### Step 4: Validate

Command:

```text
/shopadmin catalog validate
```

Validation should check:

- file syntax
- missing profile/envelope references
- conflicting rules
- invalid categories
- invalid policy assignments
- unresolved critical items
- illegal combinations
- duplicate/manual override conflicts

#### Step 5: Publish

Command:

```text
/shopadmin catalog apply
```

Publishing writes the reviewed result into the live/published catalog and triggers a safe reload path.

#### Step 6: Roll back if needed

Command:

```text
/shopadmin catalog rollback <snapshot>
```

Every apply should produce a timestamped snapshot that can be restored.

---

## 5. Catalog Policy Model

The live system should support four policy groups:

- `ALWAYS_AVAILABLE`
- `EXCHANGE`
- `SELL_ONLY`
- `DISABLED`

### 5.1 Policy meanings

#### ALWAYS_AVAILABLE

Used for narrow, intentionally unlimited-buy items.

Examples:
- landscape-protection materials
- nuisance-harvest materials
- special utility materials chosen by policy

These should usually be buy-enabled and not depend on live player stock.

#### EXCHANGE

Used for stock-backed goods.

This is the primary/default mode for whitelisted, standardized, player-supplied items.

#### SELL_ONLY

Used where admins want to reward collection/disposal without making the item part of general stock-backed redistribution.

#### DISABLED

Used for items that should not participate in the exchange.

Examples:
- progression-sensitive items
- unsafe/admin-only items
- spawn eggs
- command blocks
- structural/debug items
- items blocked by design intent

---

## 6. Classification Model

The admin system should classify items in two dimensions.

### 6.1 GUI group

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

### 6.2 Detail category

Generated/internal finer-grained classification for admin review and sub-grouping.

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
- potions
- rails
- containers
- mob_utility
- dyes

This supports rule targeting and subcategory review without forcing the player UI to become cluttered.

---

## 7. Rule System

The rule system should be ordered and deterministic.

### 7.1 Rule sources

#### Policy rules
Assign:
- policy
- gui group
- detail category
- stock profile
- eco envelope
- notes

#### Manual overrides
Explicit per-item corrections that win last.

### 7.2 Supported match types

Rules should support the following selectors:

- exact key
- wildcard key
- prefix
- suffix
- contains
- gui group
- detail category
- material traits
- derivation traits
- root-value traits
- resolved/unresolved status
- item/block flags

### 7.3 Suggested match conditions

Minimum useful conditions:

- `item-key`
- `item-key-pattern`
- `gui-group`
- `detail-category`
- `derived-from-root`
- `derivation-depth`
- `root-value-present`
- `resolved`
- `hard-disabled`
- `stackable`
- `is-block`
- `is-item`
- `edible`
- `fuel-candidate`

### 7.4 Rule ordering

Rules must be applied in file order.

Later rules may override earlier broad rules.

Manual overrides must apply after rule evaluation.

### 7.5 Rule outcome model

A rule may set any of the following:

- `policy`
- `gui-group`
- `detail-category`
- `stock-profile`
- `eco-envelope`
- `note`
- `force-include`
- `force-exclude`

The system should record the winning rule for decision tracing.

---

## 8. File Layout

Recommended admin file layout:

```text
plugins/wild_economy/
  root-values.yml
  policy-rules.yml
  manual-overrides.yml
  stock-profiles.yml
  eco-envelopes.yml
  gui-groups.yml
  generated/
    generated-catalog.yml
    generated-summary.yml
    generated-diff.yml
  published/
    exchange-items.yml
  snapshots/
    2026-03-21T10-30-00/
      exchange-items.yml
      policy-rules.yml
      manual-overrides.yml
      stock-profiles.yml
      eco-envelopes.yml
```

### 8.1 Source-of-truth distinction

- `root-values.yml`: economic anchors
- `policy-rules.yml`: broad ordered rules
- `manual-overrides.yml`: sparse explicit corrections
- `stock-profiles.yml`: reusable stock behavior profiles
- `eco-envelopes.yml`: reusable economic guardrails
- `generated/*`: proposal output, not live source
- `published/*`: live effective catalog source
- `snapshots/*`: rollback history

---

## 9. Stock Profiles

Stock behavior should be reusable and profile-driven.

### 9.1 Purpose

A stock profile defines how an item behaves operationally with respect to stock state, not its exact economic value.

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

## 10. Stock Buckets

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

- dashboards
- reports
- pricing diagnostics
- alerts
- eco-envelope behavior
- admin review

They do not need to be player-facing at first.

---

## 11. Eco Envelopes

Eco envelopes are reusable guardrail profiles that constrain economic behavior.

### 11.1 Purpose

An eco envelope should define the permitted economic behavior range for an item or group of items.

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

## 12. Admin GUI Surfaces

The GUI should assist administration but remain backed by files.

### 12.1 Catalog Review screen

Shows proposal changes grouped by:

- new item
- removed item
- policy changed
- gui group changed
- detail category changed
- stock profile changed
- eco envelope changed
- unresolved
- depth-limited
- low-confidence classification
- manual override present

### 12.2 Rule Editor

Allows admins to:

- create/edit/delete rules
- reorder rules
- preview impacted items
- inspect conflict status
- save back to `policy-rules.yml`

### 12.3 Item Inspector

For a single item, show:

- item key
- root value
- derivation status
- derivation depth
- derivation reason
- gui group
- detail category
- matched rule
- final policy
- stock profile
- eco envelope
- notes
- manual override presence

### 12.4 Stock and Eco Editor

Allows assignment and review of:

- stock profile by item/pattern/category
- eco envelope by item/pattern/category
- bucket thresholds
- profile references
- envelope references

### 12.5 Publish screen

Shows:

- count of changes
- summary of changed items
- files to write
- validation results
- snapshot name
- apply/cancel actions

---

## 13. Suggested Admin Commands

Proposed command surface:

```text
/shopadmin catalog preview
/shopadmin catalog validate
/shopadmin catalog diff
/shopadmin catalog apply
/shopadmin catalog rollback <snapshot>
/shopadmin item <item_key>
/shopadmin rule list
/shopadmin stock dashboard
/shopadmin eco inspect <item_key>
```

### 13.1 Command goals

#### `catalog preview`
Build proposal in memory and/or generated files, but do not publish.

#### `catalog validate`
Run safety and consistency validation.

#### `catalog diff`
Compare proposal to published catalog and show changes.

#### `catalog apply`
Publish proposal to live files and trigger safe reload.

#### `catalog rollback`
Restore a previous snapshot.

#### `item <item_key>`
Open detailed decision trace for one item.

#### `stock dashboard`
Open stock analytics GUI.

#### `eco inspect <item_key>`
Show economic envelope, stock profile, and current bucket state.

---

## 14. Decision Trace and Explainability

Every generated or published item should be traceable.

### 14.1 Required trace data

For each item, store or compute:

- scan facts
- root value presence
- derivation result
- derivation depth
- derivation reason
- classifier result
- classifier confidence
- matched rules
- winning rule
- manual override
- final policy
- final gui group
- final detail category
- final stock profile
- final eco envelope

### 14.2 Why this matters

This is one of the highest-value admin features because it makes the system trustworthy and debuggable.

---

## 15. Validation Rules

Validation must happen before publish.

### 15.1 Minimum validation checks

- YAML parse success
- duplicate exact overrides
- invalid wildcard syntax
- missing stock profile reference
- missing eco envelope reference
- invalid gui group
- invalid detail category
- invalid policy name
- conflicting forced include/exclude
- unresolved critical items
- blocked illegal combinations

### 15.2 Example invalid combinations

Examples of combinations the validator may reject:

- `ALWAYS_AVAILABLE` with envelope that forbids always-available
- `SELL_ONLY` with stock profile intended for public buying
- disabled item assigned public buy stock behavior
- missing gui group on live item
- missing published item key identity

---

## 16. Diff, Snapshot, and Rollback

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

---

## 17. Permissions Model

Admin tooling should support at least four permission tiers:

- `wild_economy.admin.view`
- `wild_economy.admin.catalog`
- `wild_economy.admin.economy`
- `wild_economy.admin.publish`

### 17.1 Recommended meaning

#### view
May inspect reports, traces, and dashboards.

#### catalog
May edit rules, groups, and item assignments.

#### economy
May edit stock profiles and eco envelopes.

#### publish
May apply and roll back live changes.

---

## 18. Recommended Defaults

### 18.1 Publish behavior

- explicit apply only
- snapshot on every apply
- validate before apply
- refuse publish if validation fails

### 18.2 GUI behavior

- GUI edits save back to human-readable files
- GUI always shows source file path where relevant
- GUI warns before destructive changes
- GUI shows impacted item counts before rule save

### 18.3 Rule philosophy

- broad rules first
- specific rules later
- manual overrides last
- hard-disabled safety still wins where appropriate

---

## 19. High-Value Admin Features Beyond the Basics

These are realistic and powerful additions that fit the existing plugin direction.

### 19.1 Rule impact preview

Before saving a rule, show:

- number of matched items
- sample matches
- policy changes
- profile changes
- envelope changes
- conflicts

### 19.2 Live stock dashboard

Show:

- most overstocked items
- most understocked items
- dead stock
- fast movers
- overflow items
- low-stock risk items

### 19.3 Quote simulator

Admin picks item + amount and sees:

- current stock bucket
- projected stock bucket
- estimated payout
- envelope behavior
- effect of alternate quantities

### 19.4 Dead-stock review

Surface items with low movement over time as candidates for:

- profile adjustment
- sell-only conversion
- disablement
- lower stock targets

### 19.5 Classification confidence review

Show items with weak or fallback-only classification for manual review.

---

## 20. Proposed Implementation Phases

### Phase 1: Admin intent files and proposal pipeline

Deliver:

- `policy-rules.yml`
- `manual-overrides.yml`
- `stock-profiles.yml`
- `eco-envelopes.yml`
- preview/validate/diff/apply command flow
- generated summary and diff files

### Phase 2: Decision trace and review surfaces

Deliver:

- item inspector
- rule impact preview
- decision trace data
- low-confidence review bucket

### Phase 3: GUI editing

Deliver:

- rule editor
- catalog review GUI
- stock/eco assignment GUI
- publish screen

### Phase 4: Analytics and operator tooling

Deliver:

- stock dashboard
- dead-stock report
- quote simulator
- rollback browser

---

## 21. Non-Goals for Early Phases

The early admin build should **not** try to solve everything at once.

Defer unless clearly needed:

- fully free-form scripting DSL
- live spreadsheet sync
- automatic publish on generation
- broad marketplace admin tooling
- dynamic pricing redesign
- auction or negotiation admin tools
- overly complex per-item handcrafted pricing trees

---

## 22. Summary

The recommended admin model for `wild_economy` is:

- files remain canonical
- GUI acts as a controlled editor and reviewer
- rules drive most decisions
- overrides handle edge cases
- stock behavior uses reusable stock profiles
- economic constraints use reusable eco envelopes
- all major changes are previewed, validated, and explicitly published
- every published decision is explainable
- every publish creates a rollback snapshot

This gives admins strong control without sacrificing clarity, safety, or maintainability.

---

## 23. Immediate Next-Step Recommendation

The first implementation slice should be:

1. proposal pipeline (`preview`, `validate`, `diff`, `apply`)
2. rule files (`policy-rules.yml`, `manual-overrides.yml`)
3. stock profile and eco envelope files
4. item decision trace
5. generated summary and diff reporting

This provides the highest admin value while preserving the current runtime shop design.
