# Title and Rank System Map

This document maps the current title/rank implementation in source before the next patch slice.

## Landed in source

### Config and model
- `ConfigLoader.loadTitleSettingsConfig()`
- `TitleSettingsConfig`
- `TitleOption`
- `TitleSource`
- `ResolvedTitle`
- `PlayerTitleSelection`
- `TitleDisplayMode`

### Eligibility and resolution
- `TitleEligibilityEvaluator`
- `TitleEligibilityEvaluatorImpl`
- `StoreRequirementGateServiceImpl` reused for title requirement evaluation
- `ResolvedTitleService`
- `ResolvedTitleServiceImpl`

### Persistence and cache
- `TitleSelectionService`
- `PersistentDataTitleSelectionService`
- `TitleSessionListener` warms and invalidates the resolved-title cache

### Bootstrap and placeholders
- `ServiceRegistry` loads `title-settings.yml`, wires title services, registers `TitleSessionListener`
- `WildEconomyExpansion` exposes:
  - `%wildeco_title%`
  - `%wildeco_title_key%`
  - `%wildeco_title_source%`
  - `%wildeco_title_family%`

### Shipped resource
- `src/main/resources/title-settings.yml`

## Gaps still present in source

### 1. No player-facing selection surface
There is no:
- `/titles` or `/title` command
- title GUI/menu holder/listener
- route from an existing settings surface

That means the backend can resolve and export a title, but players cannot currently choose one in normal gameplay.

### 2. Shipped title catalogue is partial
The shipped `title-settings.yml` currently contains:
- full Stormbound relic titles
- sample bread commerce titles

It does not yet include the full relic hall catalogue.

### 3. Display mode model is not wired end to end
`TitleDisplayMode` and `PlayerTitleSelection` exist, but the persisted selection service currently stores only a selected title key in player PDC.
The current runtime behavior is:
- manual selection if a selected key exists and is still eligible
- otherwise automatic fallback to the highest-priority eligible title

This is coherent for Phase 1, but the richer display-mode model is not yet implemented.

## Correct next implementation slices

### Slice A
Add the missing player-facing title selection surface:
- `/titles` command with `/title` alias
- paged title GUI
- clear/reset-to-automatic action

### Slice B
Expand `title-settings.yml` to the full relic hall catalogue using the current relic progression keys and slots.

### Slice C
Tighten deterministic ordering:
- `TitleSettingsConfig.orderedTitles()` should sort by priority descending and then by slot/key so fallback behavior is stable.

## Deliberate non-goals for this patch
- supporter title families
- live commerce crown evaluators
- full display-mode persistence (`OFF`, `AUTO_HIGHEST_RELIC`, etc.)
- rank/permission stack integration beyond title placeholder output

Those can build cleanly on the landed backend once the selection surface exists.
