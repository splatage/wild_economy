# wild_economy

A curated, Exchange-first Minecraft economy plugin for The Wild.

`wild_economy` is designed around a fast, predictable player shop experience:

- GUI-driven buying for a clean browse-and-purchase flow
- Command-driven selling for fast liquidation of bulk goods
- Player-stocked Exchange as the default mode for useful, standardized materials
- Unlimited buy-only reserved for selected nuisance or world-damaging materials
- Disabled items for progression-sensitive or gameplay-shortcutting content
- Stock-sensitive pricing with soft-cap anchoring and graduated sell-value taper
- Asynchronous persistence with in-memory runtime stock for responsive gameplay

## v1 direction

The v1 design is intentionally narrow and opinionated:

- Exchange-first economy, not a broad admin shop clone
- Standardized bulk items are the main focus
- Buying should feel simple and fast
- Selling should be command-first and low friction
- Live stock is authoritative in memory and persisted asynchronously
- Catalog and browse structures are precomputed in memory
- Soft stock caps act as pricing anchors, not hard sell blockers
- Buy-side player-stocked stock consumption is atomic per purchase action to prevent overselling

## Command surface

### Player commands

- `/shop`
  - Opens the shop GUI
- `/shop sellhand`
  - Sells the item in the playerâ€™s main hand
- `/shop sellall`
  - Sells all eligible sellable items in the player inventory
- `/shop sellcontainer`
  - Sells the contents of a supported container
- `/sellhand`
  - Top-level authoritative shortcut for selling the item in hand
- `/sellall`
  - Top-level authoritative shortcut for selling all eligible inventory items
- `/sellcontainer`
  - Top-level authoritative command for selling supported container contents

### Admin commands

- `/shopadmin`

## Current buy behavior

### Buy-side stock rules

For `PLAYER_STOCKED` items:

- player-stocked purchases consume stock atomically per buy action
- the per-click buy limit is capped at 64 items
- stock is consumed before the purchase is finalized so two buyers cannot both receive the same remaining stock

### Purchase delivery behavior

When a player purchases items, the plugin delivers the purchase through an enabled priority pipeline:

1. held shulker in the playerâ€™s main hand
2. looked-at supported placed container
3. player inventory
4. drop at the playerâ€™s feet

Important notes:

- each step is controlled by config and can be enabled or disabled independently
- disabled delivery steps are skipped
- the current looked-at container support covers chest, barrel, and placed shulker box
- if only part of the purchase can be delivered across the enabled targets, the undelivered remainder is refunded
- buy result messages include delivery details so players can see where purchased items went

This keeps purchasing flexible for bulk goods while preserving predictable, explicit behavior.

## Current sell behavior

### `/sellhand`

- Sells the held item if it is canonical, cataloged, and sell-enabled
- Uses the current stock-sensitive pricing model
- Does not hard-block purely because stock is already high

### `/sellall`

- Sells eligible inventory items in bulk
- Aggregates by item key before pricing and stock mutation
- Uses the same graduated value model as other Exchange sell paths
- Treats stock cap as a soft pricing anchor, not a hard ceiling
- **Protects shulker boxes from accidental sale** during the broad inventory sweep

### `/sellcontainer`

Intended for deliberate bulk liquidation of stored contents.

Supported v1 targets:

- looked-at chest
- looked-at barrel
- looked-at placed shulker box
- held shulker box item in the playerâ€™s main hand

Behavior:

- sells eligible contents only
- aggregates by item key before pricing and stock mutation
- leaves the container itself intact
- does not recurse into nested containers
- skips unsupported nested container items rather than opening them recursively

## Stock and pricing behavior

For `PLAYER_STOCKED` items:

- stock caps are **soft pricing anchors**, not hard intake ceilings
- players may continue selling above the cap
- sell value tapers downward as stock approaches the cap
- once stock is at or above the cap, sell value floors at the configured minimum band
- a single sell action is priced as one aggregated batch per item key
- batch payout uses a start-to-end average with a floor split if the batch crosses the cap
- payout is rounded once at the end of the batch quote

## Shulker safety rules

To protect player trust and prevent accidental storage loss:

- filled shulker boxes are **not** treated as canonical sellable Exchange items
- `/sellall` skips shulker box items rather than selling them automatically
- intentional liquidation of shulker contents should happen through `/sellcontainer`

This means:

- carrying a filled shulker will not accidentally sell it as a normal item
- broad cleanup with `/sellall` remains safe around portable storage
- selling container contents is an explicit opt-in action

## Architecture status

The current foundation includes:

- Hikari-backed database access
- in-memory authoritative stock cache
- atomic in-memory stock consumption for player-stocked buys
- asynchronous stock persistence
- asynchronous transaction logging
- precomputed catalog and browse indexes
- batched stock flushing with periodic flush behavior
- stock metrics support in the runtime persistence layer

## Documentation

Key design documents live under `docs/`:

- `docs/database-design.md`
- `docs/technical-spec_v1.md`
- `docs/wild_economy-v1_scope.md`
- `docs/sell-container-feature-scope.md`

## Status

Active v1 implementation with:

- Exchange buy flow
- top-level sell commands
- grouped sell pricing by item key
- soft-cap batch/trapezoid sell pricing
- atomic buy-side stock handling
- shulker sale protection
- intentional container-content selling
- configurable purchase delivery routing
- memory-first stock runtime and async persistence foundation
- Exchange-first economy, not a broad admin shop clone
- Standardized bulk items are the main focus
- Buying should feel simple and fast
- Selling should be command-first and low friction
- Live stock is authoritative in memory and persisted asynchronously
- Catalog and browse structures are precomputed in memory
- Soft stock caps act as pricing anchors, not hard sell blockers
- Buy-side player-stocked stock consumption is atomic per purchase action to prevent overselling

## Command surface

### Player commands

- `/shop`
  - Opens the shop GUI
- `/shop sellhand`
  - Sells the item in the player’s main hand
- `/shop sellall`
  - Sells all eligible sellable items in the player inventory
- `/shop sellcontainer`
  - Sells the contents of a supported container
- `/sellhand`
  - Top-level authoritative shortcut for selling the item in hand
- `/sellall`
  - Top-level authoritative shortcut for selling all eligible inventory items
- `/sellcontainer`
  - Top-level authoritative command for selling supported container contents

### Admin commands

- `/shopadmin`

## Current sell behavior

### `/sellhand`

- Sells the held item if it is canonical, cataloged, and sell-enabled
- Uses the current stock-sensitive pricing model
- Does not hard-block purely because stock is already high

### `/sellall`

- Sells eligible inventory items in bulk
- Aggregates by item key before pricing and stock mutation
- Uses the same graduated value model as other Exchange sell paths
- Treats stock cap as a soft pricing anchor, not a hard ceiling
- **Protects shulker boxes from accidental sale** during the broad inventory sweep

### `/sellcontainer`

Intended for deliberate bulk liquidation of stored contents.

Supported v1 targets:

- looked-at chest
- looked-at barrel
- looked-at placed shulker box
- held shulker box item in the player’s main hand

Behavior:

- sells eligible contents only
- aggregates by item key before pricing and stock mutation
- leaves the container itself intact
- does not recurse into nested containers
- skips unsupported nested container items rather than opening them recursively

## Stock and pricing behavior

For `PLAYER_STOCKED` items:

- stock caps are **soft pricing anchors**, not hard intake ceilings
- players may continue selling above the cap
- sell value tapers downward as stock approaches the cap
- once stock is at or above the cap, sell value floors at the configured minimum band
- a single sell action is priced as one aggregated batch per item key
- batch payout uses a start-to-end average with a floor split if the batch crosses the cap
- payout is rounded once at the end of the batch quote

Buy-side behavior:

- player-stocked purchases consume stock atomically per buy action
- the per-click buy limit is capped at 64 items
- stock is consumed before the purchase is finalized so two buyers cannot both receive the same remaining stock

## Shulker safety rules

To protect player trust and prevent accidental storage loss:

- filled shulker boxes are **not** treated as canonical sellable Exchange items
- `/sellall` skips shulker box items rather than selling them automatically
- intentional liquidation of shulker contents should happen through `/sellcontainer`

This means:

- carrying a filled shulker will not accidentally sell it as a normal item
- broad cleanup with `/sellall` remains safe around portable storage
- selling container contents is an explicit opt-in action

## Architecture status

The current foundation includes:

- Hikari-backed database access
- in-memory authoritative stock cache
- atomic in-memory stock consumption for player-stocked buys
- asynchronous stock persistence
- asynchronous transaction logging
- precomputed catalog and browse indexes
- batched stock flushing with periodic flush behavior
- stock metrics support in the runtime persistence layer

## Documentation

Key design documents live under `docs/`:

- `docs/database-design.md`
- `docs/technical-spec_v1.md`
- `docs/wild_economy-v1_scope.md`
- `docs/sell-container-feature-scope.md`

## Status

Active v1 implementation with:

- Exchange buy flow
- top-level sell commands
- grouped sell pricing by item key
- soft-cap batch/trapezoid sell pricing
- atomic buy-side stock handling
- shulker sale protection
- intentional container-content selling
- memory-first stock runtime and async persistence foundation
