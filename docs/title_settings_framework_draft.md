# wild_economy title settings framework draft

This draft is grounded in the current repo shape:

- `store-products.yml` already models gated, visible-locked progression with:
  - `requirements`
  - `visibility-when-unmet`
  - `locked-message`
- `StoreEligibilityServiceImpl` already evaluates:
  - `ENTITLEMENT`
  - `PERMISSION`
  - `STATISTIC`
  - `STATISTIC_MATERIAL`
  - `STATISTIC_ENTITY`
  - `ADVANCEMENT`
  - `CUSTOM_COUNTER`
- `StoreProgressServiceImpl` already supplies advancement checks and persistent per-player counters via `PersistentDataContainer`
- `WildEconomyExpansion` already provides a PlaceholderAPI surface and is the right insertion point for title placeholders
- the current store runtime state is already lazy-load + cached for entitlements, which is the right ownership truth for relic titles

The goal is to add **title selection** without making the placeholder path expensive, and without turning earned titles into another buyable product family.

---

## 1. Design goal

Titles should have three independent layers:

1. **earned title eligibility**
2. **selected active title**
3. **resolved placeholder output**

The system should support at least these title families:

- relic hall titles
- commerce milestone titles
- commerce crown / leaderboard titles
- supporter / cosmetic titles later
- event titles later

The selection UI should feel like the store:

- slotted options
- locked-but-visible choices where appropriate
- progress lines
- inspirational locked messages

But selection is not purchase. It should be a settings action.

---

## 2. What should stay as source of truth

### 2.1 Relic titles
Keep relic ownership truth exactly where it already lives:

- `prestige.stormbound.1`
- `prestige.stormbound.2`
- ...
- `prestige.stormbound.5`

No separate relic-title unlock table is needed for phase 1.

Derived title eligibility can be expressed directly from these entitlements.

### 2.2 Commerce milestone titles
These should be granted as title entitlements by a dedicated commerce-title evaluator.

Examples:

- `title.commerce.bread.apprentice`
- `title.commerce.bread.baker`
- `title.commerce.bread.master_baker`

### 2.3 Commerce crown titles
These should also become entitlements, but dynamic ones.

Examples:

- `title.commerce.bread.weekly_leader`
- `title.commerce.bread.monthly_leader`
- `title.commerce.bread.all_time_leader`

These must be granted and revoked by a scheduled evaluator, not computed in placeholder requests.

---

## 3. Core architecture

### 3.1 New config: `title-settings.yml`

This should be a settings-facing config file, not a store product file.

Suggested top-level shape:

```yml
categories:
  relic_titles:
    display-name: "Relic Titles"
    icon: ECHO_SHARD
    slot: 0
    requirements: {}
    visibility-when-unmet: SHOW_LOCKED
    locked-message: "Earn relic titles through the Relic Hall."

  commerce_titles:
    display-name: "Commerce Titles"
    icon: BREAD
    slot: 1
    visibility-when-unmet: SHOW_LOCKED
    locked-message: "Earn titles through supply and market leadership."

options:
  stormbound_galefoot:
    category: relic_titles
    display-name: "Galefoot"
    icon: DIAMOND_BOOTS
    slot: 0
    title-text: "Galefoot"
    source: RELIC
    lore:
      - "The first step into the open sky."
    visibility-when-unmet: SHOW_LOCKED
    locked-message: "Advance in Stormbound to unlock this title."
    requirements:
      all-of:
        - type: ENTITLEMENT
          entitlement: "prestige.stormbound.1"

  bread_master_baker:
    category: commerce_titles
    display-name: "Master Baker"
    icon: BREAD
    slot: 10
    title-text: "Master Baker"
    source: COMMERCE
    lore:
      - "Earned through a lifetime of provisioning the market."
    visibility-when-unmet: SHOW_LOCKED
    locked-message: "Supply more bread to unlock this title."
    requirements:
      all-of:
        - type: ENTITLEMENT
          entitlement: "title.commerce.bread.master_baker"
```

### 3.2 New domain package

Suggested package root:

- `com.splatage.wild_economy.title`

Suggested subpackages:

- `title.config`
- `title.model`
- `title.service`
- `title.state`
- `title.placeholder`
- `title.gui` (or under existing gui package if preferred)

### 3.3 New model types

Suggested first-pass model types:

- `TitleSettingsConfig`
- `TitleCategory`
- `TitleOption`
- `TitleSource` (`RELIC`, `COMMERCE`, `SUPPORTER`, `EVENT`)
- `TitleDisplayMode` (`OFF`, `MANUAL`, `AUTO_HIGHEST_RELIC`, `AUTO_BEST_COMMERCE`, `AUTO_CURRENT_CROWN`)
- `PlayerTitleSelection`
- `ResolvedActiveTitle`

---

## 4. Reuse vs extraction

### 4.1 What should be reused

The current store requirement grammar is already right.

Do not invent a second requirement DSL.

Title settings should reuse:

- `StoreRequirement`
- `StoreRequirementType`
- `StoreVisibilityWhenUnmet`

### 4.2 What should be extracted

`StoreEligibilityServiceImpl` currently mixes two concerns:

1. generic requirement evaluation
2. store-product-specific ownership and tier cooldown logic

Title settings only need the generic requirement evaluation.

Recommended extraction:

- new shared service: `EligibilityRequirementService`
- move generic requirement evaluation logic there:
  - entitlement
  - permission
  - statistic
  - statistic_material
  - statistic_entity
  - advancement
  - custom_counter
- `StoreEligibilityServiceImpl` keeps product-specific logic:
  - permanent unlock ownership checks
  - tier cooldown enforcement
- title settings call the shared requirement evaluator directly

This avoids copy-pasting the requirement engine into a second settings system.

---

## 5. State and persistence

### 5.1 What must be persisted

Only the player’s **selection** needs dedicated persistence.

Suggested data:

- `player_uuid`
- `selected_title_key`
- `display_mode`
- `updated_at`

### 5.2 What does not need a new table

Do not persist “owned relic titles” separately.
They are already derivable from store entitlements.

Do not persist “currently holds crown title” separately if you already have a runtime snapshot service; only persist if later multi-server durability becomes necessary.

### 5.3 Suggested table

Prefer a separate store-adjacent table under the store prefix:

- `${store_prefix}title_settings`

Reason:
- this is a server-facing shop/cosmetic system
- it is not an economy balance mutation
- it aligns more naturally with the store domain than exchange or economy core

If later you want network-wide shared titles, this can be revisited.

---

## 6. Runtime services

### 6.1 `TitleSettingsService`

Responsibilities:

- load/save player title selection
- expose categories and visible title options
- validate whether a title option is currently selectable
- set active title choice
- expose current active title mode

### 6.2 `TitleResolverService`

Responsibilities:

- resolve the player’s effective title from:
  - selection mode
  - selected title key
  - earned title eligibility
  - crown title snapshot if in crown auto mode
- choose fallback title if selected one is no longer eligible
- produce a `ResolvedActiveTitle`

### 6.3 `TitleRuntimeCache`

Responsibilities:

- keep resolved active title text per player in memory
- invalidate/update on:
  - join
  - selection change
  - entitlement grant affecting titles
  - commerce milestone grant/revoke
  - crown snapshot refresh

This cache is what the placeholder reads.

---

## 7. Placeholder design

### 7.1 Extend `WildEconomyExpansion`

Do not build a second PlaceholderExpansion.

Extend the existing `WildEconomyExpansion` with injected title services.

Suggested new placeholders:

- `%wildeco_title%` → active rendered title
- `%wildeco_title_source%` → `relic`, `commerce`, `supporter`, `event`
- `%wildeco_title_key%` → internal selected/resolved key
- `%wildeco_title_mode%` → `manual`, `auto_highest_relic`, etc.

### 7.2 Hot-path rule

`%wildeco_title%` must return a cached string only.

It must not:

- evaluate statistics
- query the database
- scan leaderboard standings
- recompute requirement trees
- reload player selection state

That work must happen earlier and update the cache.

This is critical because PlaceholderAPI placeholders can be hammered by chat, tab, scoreboards, and nametag plugins.

---

## 8. Commerce titles: performance-safe model

### 8.1 Milestone titles

Milestone titles are cheap:

- evaluate on sale contribution update or on periodic batch
- grant/revoke milestone entitlements if thresholds change

Suggested examples:

- `title.commerce.bread.apprentice`
- `title.commerce.bread.baker`
- `title.commerce.bread.master_baker`

### 8.2 Crown / leaderboard titles

Crown titles are dynamic and must not be evaluated live in placeholders.

Recommended model:

- new `CommerceTitleSnapshotService`
- scheduled refresh cadence, e.g. every 30–120 seconds depending on tolerance
- evaluate top supplier per configured leaderboard title family
- update in-memory crown-holder snapshot
- optionally grant/revoke crown entitlements if you want title options to use the same requirement model

Suggested first-wave title families:

- bread
- stone / masonry
- timber / forestry
- fish / angling
- redstone / engineering

---

## 9. GUI / settings UX

### 9.1 View shape

The settings GUI should feel like the shop, but not behave like it.

Suggested menu layers:

- `Title Settings`
  - `Relic Titles`
  - `Commerce Titles`
  - `Supporter Titles` (later)
  - `Event Titles` (later)

Each title option tile should show:

- icon
- display name
- lore
- active marker if selected
- locked progress lines if unmet

### 9.2 Selection action

Selecting a title should:

- verify eligibility
- persist the new selected key or mode
- refresh the player’s resolved title cache
- optionally send a confirmation chat line

No economy mutation.
No purchase audit required.

### 9.3 Modes

Suggested modes exposed in settings:

- `Off`
- `Manual`
- `Auto: Highest Relic`
- `Auto: Best Commerce`
- `Auto: Current Crown`

Manual mode then uses the visible title options UI.

---

## 10. Resolver priority rules

Suggested resolution order:

### Manual mode

1. if selected title key exists and is eligible, use it
2. otherwise fall back to highest eligible relic title
3. otherwise return empty title

### Auto highest relic

- choose the highest eligible relic title by configured priority order

### Auto best commerce

- prefer highest commerce milestone title
- if none, prefer current crown title if policy says crowns outrank milestones

### Auto current crown

- choose current crown title if present
- otherwise fall back to best commerce milestone

This keeps player-facing behavior stable.

---

## 11. Suggested priority field

Add a `priority` integer to `TitleOption`.

This makes auto-resolution deterministic and avoids implicit ordering tricks.

Example:

- `Stormbound I` → 100
- `Stormbound II` → 200
- ...
- `Stormbound V` → 500
- `Master Baker` → 350
- `Baker of the Week` → 360

You can then decide whether crowns outrank milestones or vice versa by number.

---

## 12. Bootstrap and integration seams

### 12.1 `ConfigLoader`

Add:

- `loadTitleSettingsConfig()`

This should mirror the style of `loadStoreProductsConfig()` and parse:

- categories
- options
- requirements
- visibility-when-unmet
- locked-message

### 12.2 `ManagedConfigMaterializer`

Add managed file support for:

- `title-settings.yml`

### 12.3 `ServiceRegistry`

Wire in:

- `TitleSettingsConfig`
- `TitleSettingsService`
- `TitleResolverService`
- `TitleRuntimeCache`
- optional `CommerceTitleSnapshotService`

Then inject title services into `WildEconomyExpansion`.

### 12.4 Listeners / triggers

Add updates on:

- player join
- title selection change
- store entitlement grant if relevant to titles
- commerce milestone updates
- scheduled crown refresh

---

## 13. Minimal first implementation scope

Phase 1 should stay narrow.

### Phase 1

Implement:

- title-settings config parsing
- title selection persistence
- resolved title cache
- `%wildeco_title%`
- relic title family only
- manual mode only

This gets the architecture right with minimal moving parts.

### Phase 2

Add:

- auto modes
- commerce milestone titles
- commerce crown titles
- extra placeholders

### Phase 3

Add:

- supporter titles
- event titles
- richer GUI polish

---

## 14. Why this is a good fit for the current repo

It respects what the repo already does well:

- entitlements remain canonical ownership truth
- requirement grammar remains unified
- placeholder hot path stays cache-only
- expensive relative ranking work stays out of PlaceholderAPI
- settings and store stay conceptually separate

It also avoids the worst mistakes:

- no live leaderboard queries in placeholders
- no duplicate requirement DSL
- no fake “purchase” flow for earned titles
- no parallel relic ownership state

---

## 15. Recommended immediate next step

Build Phase 1 only:

1. `title-settings.yml`
2. title option/category models
3. extracted shared requirement evaluator
4. player title selection persistence
5. resolved title cache
6. `%wildeco_title%`
7. one relic title family as exemplar (Stormbound)

That will prove the architecture without dragging in commerce crowns prematurely.
