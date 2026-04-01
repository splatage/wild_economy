# Title Settings Phase-1 Overlay Notes

This overlay is designed against the uploaded `wild_economy` repo and intentionally stays narrow:

- earned title state remains driven by existing entitlements and progression
- title *selection* is stored separately
- `%wildeco_title%` should return a cached resolved value only
- first live family should be `stormbound`
- commerce crowns/milestones are config-shaped here, but entitlement grant logic is still phase 2

## New files included

- `config/TitleSettingsConfig.java`
- `title/model/*`
- `title/service/*`
- `title/eligibility/TitleEligibilityEvaluator.java`
- `config/title-settings.example.yml`

## Existing files that should be patched

### `ConfigLoader`
Add:
- `loadTitleSettingsConfig()`
- shared requirement parsing extraction from the current private store methods

Strong recommendation:
- extract `parseStoreRequirements`, `parseStoreVisibilityWhenUnmet`, `resolveStoreRequirementKey`,
  and `validateStoreRequirement` into a shared helper used by both `store-products.yml` and `title-settings.yml`

### `StoreEligibilityServiceImpl`
Strong recommendation:
- extract a reusable requirement evaluator component instead of duplicating requirement logic
- phase-1 title selection should reuse the exact same requirement semantics as the store

### `ServiceRegistry`
Wire:
- `TitleSettingsConfig`
- `TitleSelectionService`
- `ResolvedTitleService`
- a `TitleEligibilityEvaluator` adapter backed by the shared requirement evaluator
- updated `WildEconomyExpansion` constructor

### `WildEconomyExpansion`
Add at least:
- `%wildeco_title%`
- `%wildeco_title_source%`
- `%wildeco_title_key%`
- `%wildeco_title_family%`

Placeholder behavior must remain cache-only.

## Suggested phase-1 bootstrap wiring

1. Load `TitleSettingsConfig`
2. Build `PersistentDataTitleSelectionService("wild_economy")`
3. Build `TitleEligibilityEvaluator` from extracted shared requirement evaluator
4. Build `ResolvedTitleServiceImpl`
5. Register placeholder expansion with resolved-title dependency
6. On player join:
   - warm resolved title cache
7. On title selection change:
   - update selection
   - invalidate + resolve cache

## Important performance rule

Never evaluate:
- statistics
- advancements
- entitlement ownership
- commerce ranking

from inside `%wildeco_title%`.

Resolve once, cache, then return the cached text.
