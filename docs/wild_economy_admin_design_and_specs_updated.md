# wild_economy Admin Design and Specifications

Revision date: 2026-03-22  
Status: Updated design and implementation spec  
Scope: Admin-facing catalog, classification, policy behavior, review tooling, and GUI workflow for `wild_economy`

---

## 1. Purpose

This document defines the current and intended admin architecture for managing the `wild_economy` Exchange catalog.

It has been updated to reflect the implemented state of the project, including:

- Phase 1 backend/admin pipeline
- Phase 2 review and inspection tooling
- Phase 3 read-only admin review GUI
- initial GUI-based manual override editing
- config-backed policy behavior profiles

The design goal remains to make catalog and economy administration:

- rule-driven first
- override-driven second
- GUI-assisted
- human-readable on disk
- safe to preview, validate, publish, and roll back

---

## 2. Current State Summary

The admin/catalog system now has four major layers:

### 2.1 Catalog generation and planning
The plugin can generate a proposed catalog from:

- root values
- recipe graph + fallbacks
- derivation rules
- category classification
- policy rules
- manual overrides
- stock profiles
- eco envelopes
- policy behavior profiles

### 2.2 Review and reporting
The plugin now generates review-oriented outputs, including:

- generated catalog
- generated diff
- generated summary
- generated validation report
- item decision traces
- rule impact reports
- review buckets

### 2.3 Command-based admin inspection
The plugin now supports a real item inspector via `/shopadmin item <item_key>`, exposing:

- category
- derivation status
- base suggested policy
- final policy
- runtime behavior
- pricing
- winning rule
- matched-but-lost rules
- manual override presence
- review-bucket membership

### 2.4 GUI-based admin review
The plugin now has a read-only admin review GUI, plus an initial safe mutation path for manual item overrides.

This includes:

- admin root menu
- review bucket browser
- review bucket subgroup detail
- rule impact browser
- rule impact sample detail
- item inspector GUI
- apply confirmation
- manual override editor

---

## 3. Design Principles

### 3.1 Files remain the source of truth

Admin intent is stored in human-readable files that can be:

- version controlled
- diffed
- reviewed
- edited by hand
- restored from backup

The GUI is a review and editing surface over those files, not a separate source of truth.

### 3.2 Preview before publish

Generation and admin edits must support a staging workflow:

1. generate proposal
2. review proposal
3. validate proposal
4. publish explicitly
5. roll back if necessary

No major catalog mutation should happen silently.

### 3.3 Rule-first administration

Admins should usually fix catalog behavior by changing:

- rules
- profiles
- envelopes
- policy behavior definitions

Per-item overrides remain important, but should be sparse and explicit.

### 3.4 Explainability

For any item, the system should be able to explain:

- why it was included or excluded
- how it was derived
- which rule matched
- which rule won
- which matched rules lost
- which manual override applies
- what the final runtime behavior is

### 3.5 Runtime safety

Admin tooling must not compromise the fast runtime path of the player-facing exchange.

Heavy generation, diffing, validation, and reporting stay off the gameplay-critical path.

---

## 4. Admin Model Overview

The admin system now separates concerns into five layers:

### 4.1 Policy assignment
Which high-level policy an item belongs to:

- `ALWAYS_AVAILABLE`
- `EXCHANGE`
- `SELL_ONLY`
- `DISABLED`

### 4.2 Policy behavior profile
What that policy actually does operationally.

Policy behavior is now intended to be config-backed rather than permanently hardcoded.

This includes behavior such as:

- runtime mode
- buy enabled
- sell enabled
- stock-backed yes/no
- unlimited-buy yes/no
- requires player stock to buy yes/no

### 4.3 Stock profile
Defines stock-related behavior, such as:

- stock cap
- low-stock threshold
- overflow threshold
- turnover behavior

### 4.4 Eco envelope
Defines pricing/economic behavior, such as:

- base buy multiplier
- base sell multiplier
- banded sell behavior
- floor/penalty behavior

### 4.5 Category and classification
Defines how items are grouped and reviewed.

This includes:

- broad display category / GUI group
- generated/internal category used for review and reporting

---

## 5. File Layout

Recommended / current admin file layout:

```text
plugins/wild_economy/
  root-values.yml
  policy-rules.yml
  policy-profiles.yml
  manual-overrides.yml
  stock-profiles.yml
  eco-envelopes.yml
  generated/
    generated-catalog.yml
    generated-summary.yml
    generated-diff.yml
    generated-validation.yml
    generated-rule-impacts.yml
    generated-review-buckets.yml
    item-decision-traces.yml
  published/
    exchange-items.yml
  snapshots/
    <timestamp>/
      ...
```

### 5.1 File roles

- `root-values.yml`: economic anchors
- `policy-rules.yml`: broad ordered assignment rules
- `policy-profiles.yml`: behavior contracts behind policies
- `manual-overrides.yml`: per-item corrections and explicit admin choices
- `stock-profiles.yml`: stock behavior definitions
- `eco-envelopes.yml`: pricing/economic guardrails
- `generated/*`: proposal and review output
- `published/*`: live/effective catalog
- `snapshots/*`: rollback history

---

## 6. Policy Assignment vs Policy Behavior

This is now a key distinction in the admin design.

### 6.1 Policy assignment answers:
What general policy bucket is this item in?

Examples:
- `ALWAYS_AVAILABLE`
- `EXCHANGE`
- `SELL_ONLY`
- `DISABLED`

### 6.2 Policy behavior answers:
What does that mean operationally?

Examples:
- can players buy it?
- can players sell it?
- is it unlimited-buy?
- is it stock-backed?
- does it require player stock to buy?

### 6.3 Why this matters

Previously, behavior such as “unlimited buy implies cannot sell to server” was bundled too tightly into internal policy semantics.

That is too rigid.

The intended direction is that admins can edit policy behavior through `policy-profiles.yml`, so cases like:

- unlimited buy **and** server sell allowed

can exist without requiring code changes.

---

## 7. Catalog Policy Model

The item assignment model still uses four major policy states:

- `ALWAYS_AVAILABLE`
- `EXCHANGE`
- `SELL_ONLY`
- `DISABLED`

### 7.1 Intended meaning

#### ALWAYS_AVAILABLE
Used for intentionally unlimited-buy items.

#### EXCHANGE
Used for stock-backed items that players buy from and sell to.

#### SELL_ONLY
Used for items that players can sell but not publicly buy.

#### DISABLED
Used for items outside the exchange.

### 7.2 Important design update

These policy names should not be treated as the full behavior model.

Their effective operational behavior is controlled by `policy-profiles.yml`.

---

## 8. Policy Profiles

Policy profiles define the editable behavior contract behind assigned policies.

### 8.1 Example fields

Suggested / current direction:

- `runtime-mode`
- `buy-enabled`
- `sell-enabled`
- `stock-backed`
- `unlimited-buy`
- `requires-player-stock-to-buy`

### 8.2 Example intent

Examples:

- `always_available`
  - unlimited buy
  - buy enabled
  - may or may not allow selling, depending on admin choice

- `exchange`
  - player-stocked buy/sell
  - stock-backed
  - requires player stock to buy

- `sell_only`
  - sell enabled
  - no public buy path

- `disabled`
  - buy disabled
  - sell disabled

### 8.3 Admin implications

The admin UI and inspector should expose both:

- **internal policy assignment**
- **effective runtime behavior**

So admins can reason in behavioral terms, not just enum names.

---

## 9. Rule System

The rule system is ordered and deterministic.

### 9.1 Rule sources

#### Policy rules
Broad ordered rules assign:
- policy
- stock profile
- eco envelope
- notes
- category/group fields when supported

#### Manual overrides
Explicit per-item corrections that win last.

### 9.2 Supported matching dimensions

Rules may match on:
- exact item key
- wildcard item key
- derivation depth
- derivation reason
- categories
- material traits
- resolved/unresolved state
- other admin-relevant item facts

### 9.3 Fallback rule behavior

Matchless rules such as `default-exchange` are treated as true fallback rules and should only apply when no specific rule with match criteria wins.

This is now a locked behavior expectation.

---

## 10. Classification Model

The current system classifies items into reviewable categories.

### 10.1 Broad admin/player-facing categories
Examples:
- `WOODS`
- `STONE`
- `DECORATION`
- `FOOD`
- `MISC`

### 10.2 Review-oriented grouping
The system also generates review buckets that surface items such as:

- live items still in `MISC`
- no-root-path items
- blocked-path items
- manual overrides
- sell-only review items

### 10.3 Remaining goal
Continue reducing unnecessary `MISC` spillover through classifier refinement and admin override support.

---

## 11. Core Admin Workflow

### 11.1 Generate a proposal

Command:

```text
/shopadmin catalog preview
```

### 11.2 Validate proposal

Command:

```text
/shopadmin catalog validate
```

### 11.3 Compare proposal to live

Command:

```text
/shopadmin catalog diff
```

### 11.4 Publish explicitly

Command:

```text
/shopadmin catalog apply
```

### 11.5 Inspect an item

Command:

```text
/shopadmin item <item_key>
```

### 11.6 Review in GUI

Command:

```text
/shopadmin
```

or:

```text
/shopadmin gui
```

---

## 12. Command-Based Review Tooling

The command inspector is now a real admin tool, not just a debug output.

### 12.1 Item inspector output includes

- item key / display name
- category
- derivation reason and depth
- base suggested policy
- final policy
- runtime behavior
- buy/sell visibility
- stock-backed / unlimited-buy behavior
- stock profile
- eco envelope
- winning rule
- matched-but-lost rules
- manual override status
- review-bucket membership
- notes

### 12.2 Design goal

The inspector should explain not only what happened, but why.

---

## 13. Generated Review Reports

The system now generates review-oriented reports that admins can inspect outside the game.

### 13.1 Rule impacts
`generated-rule-impacts.yml` includes:

- match count
- win count
- loss count
- matched samples
- winning samples
- lost samples
- lost-to-rule counts
- lost-to-policy counts

### 13.2 Review buckets
`generated-review-buckets.yml` includes grouped review buckets such as:

- `live-misc-items`
- `no-root-path`
- `blocked-paths`
- `manual-overrides`
- `sell-only-review`

Buckets now include subgroup counts and subgroup sample items.

### 13.3 Item traces
`item-decision-traces.yml` remains the deepest backend trace source.

---

## 14. GUI-Based Admin Review

The read-only admin GUI is now substantially implemented.

### 14.1 Implemented screens

- admin root menu
- review bucket list
- review bucket detail
- review bucket subgroup detail
- rule impact list
- rule impact detail
- rule impact sample detail
- item inspector GUI

### 14.2 Implemented GUI behavior

- drilldown navigation
- back navigation
- top issue surfacing
- apply confirmation safety step
- subgroup drilldown
- sample drilldown
- inspector context buttons

### 14.3 Current scope boundary

The GUI remains primarily review-oriented, not a full editor.

---

## 15. Manual Override Editing

The first safe mutation path is now in place.

### 15.1 Current editable scope
From the item inspector GUI, admins can edit/create/remove per-item manual overrides for:

- policy assignment
- stock profile
- eco envelope
- note (currently limited/template-oriented in earlier design stages)

### 15.2 Safety
This path is intentionally narrow and file-backed.

It is meant to be:
- low-risk
- per-item
- reviewable
- compatible with the existing `manual-overrides.yml` source of truth

### 15.3 Current boundary
This is **not** yet a full GUI rule editor.

---

## 16. Validation and Safety

Validation remains a core part of the system.

### 16.1 Current validation goals

- parse admin files safely
- detect bad references
- surface suspicious catalog outcomes
- avoid silent publish mistakes

### 16.2 GUI safety
Current GUI safety includes:
- read-only review default
- apply confirmation step
- manual override editing only on a narrow per-item basis
- no broad rule mutation in GUI yet

---

## 17. Permissions

The intended permission model remains:

- `wild_economy.admin.view`
- `wild_economy.admin.catalog`
- `wild_economy.admin.economy`
- `wild_economy.admin.publish`

The exact command/GUI enforcement should continue to align with these responsibilities.

---

## 18. What Is Complete

### 18.1 Phase 1
Complete enough to freeze:
- backend catalog/admin pipeline
- preview / validate / diff / apply
- rules / overrides / profiles / envelopes
- generated reports
- decision traces

### 18.2 Phase 2
Complete enough to use:
- item inspector
- rule impacts
- review buckets
- grouped review reporting
- loss reporting
- better chat presentation

### 18.3 Phase 3 review GUI
Substantially complete for read-only review:
- root menu
- bucket browser
- rule browser
- item inspector GUI
- drilldown screens
- apply confirmation
- review navigation polish

### 18.4 Initial mutation
Started:
- manual override editing

---

## 19. What Remains

### 19.1 High-value next steps

#### Policy behavior editing
The system should continue moving toward policy behavior being admin-editable through `policy-profiles.yml`, and later possibly via GUI.

#### Category/classification override editing
This is a strong next mutation target, especially to address `live-misc-items`.

#### Review-guided quick actions
Examples:
- reclassify from review buckets
- override from bucket context
- jump to likely corrective action from inspector

### 19.2 Deferred items

Still deferred:
- full GUI rule editor
- full GUI stock-profile editor
- full GUI eco-envelope editor
- rollback browser UI
- stock dashboard UI
- quote simulator UI

---

## 20. Recommended Next Development Direction

The current best next step is:

### 20.1 Make policy behavior truly admin-editable
Continue extracting behavior out of hardcoded policy semantics and into config-backed policy profiles.

This is especially important for cases like:
- unlimited-buy items that admins still want to allow selling back to the server

### 20.2 Then expand safe mutation gradually
Recommended order:
1. policy behavior profiles
2. category override editing
3. quick review-bucket corrective actions
4. only later, broader rule editing

---

## 21. Summary

The `wild_economy` admin/catalog system now has:

- a stable backend generation and validation pipeline
- meaningful review reports
- a useful item inspector
- a substantial read-only admin review GUI
- a first safe mutation path through manual overrides
- a clear design direction toward config-backed policy behavior

The key architectural direction is now:

- assignment policy and runtime behavior must remain separable
- files remain the source of truth
- review comes before publish
- GUI mutation should expand gradually and safely
- admin tooling should explain behavior in operational terms, not only internal enum names

This is now a real admin system, not just scaffolding.
