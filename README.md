# wild_economy

A curated, Exchange-first Minecraft economy plugin for The Wild.

`wild_economy` is designed around a fast, predictable player shop experience:

- GUI-driven buying for a clean browse-and-purchase flow
- Command-driven selling for fast liquidation of bulk goods
- Player-stocked Exchange as the default mode for useful, standardized materials
- Unlimited-buy exceptions reserved for selected nuisance or world-damaging materials
- Disabled items for progression-sensitive or gameplay-shortcutting content
- Linear stock-sensitive sell pricing through reusable eco envelopes
- Asynchronous persistence with in-memory runtime stock for responsive gameplay

## v1 direction

The v1 design is intentionally narrow and opinionated:

- Exchange-first economy, not a broad admin shop clone
- Standardized bulk items are the main focus
- Buying should feel simple and fast
- Selling should be command-first and low friction
- Live stock is authoritative in memory and persisted asynchronously
- Catalog and browse structures are precomputed in memory
- Soft stock anchors shape value, not hard sell blockers
- Buy-side player-stocked stock consumption is atomic per purchase action to prevent overselling
- Runtime pricing is resolved from the published `exchange-items.yml` catalog and current live stock rather than an external worth file
- Admin generation still uses `root-values.yml`, `stock-profiles.yml`, `eco-envelopes.yml`, rules, and overrides to publish that runtime catalog

## Command surface

### Player commands

- `/shop` — Opens the shop GUI
- `/shop sellhand` — Sells the item in the player’s main hand
- `/shop sellall` — Sells all eligible sellable items in the player inventory
- `/shop sellcontainer` — Sells the contents of a supported container
- `/sellhand` — Top-level authoritative shortcut for selling the item in hand
- `/sellall` — Top-level authoritative shortcut for selling all eligible inventory items
- `/sellcontainer` — Top-level authoritative command for selling supported container contents

### Admin commands

- `/shopadmin`

## Current buy behavior

### Buy-side stock rules

For `PLAYER_STOCKED` items:

- player-stocked purchases consume stock atomically per buy action
- the per-click buy limit is capped at 64 items
- stock is consumed before the purchase is finalized so two buyers cannot both receive the same remaining stock
- buy pricing is quoted once per purchase transaction and honored for that transaction

### Detail-menu quoted price behavior

When a player opens an item detail menu:

- the detail menu captures the shown buy unit price
- the Buy 1 / 8 / 64 buttons use that captured quoted price
- that quoted price is honored for a click while the menu quote remains fresh
- stale quoted detail menus are refreshed before purchase instead of honoring an old quote indefinitely
- the current quote lifetime is 30 seconds
- after a successful purchase, the detail menu reopens with a fresh live quote for the next buy

This preserves the “shown price is the price you click” rule without letting an old menu pin a stale price forever.

### Purchase delivery behavior

When a player purchases items, the plugin delivers the purchase through an enabled priority pipeline:

1. held shulker in the player’s main hand
2. looked-at supported placed container
3. player inventory
4. drop at the player’s feet

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
- Uses the current stock-sensitive pricing model where applicable
- Does not hard-block purely because stock is already high

### `/sellall`

- Sells eligible inventory items in bulk
- Aggregates by item key before pricing and stock mutation
- Uses the same linear value model as other Exchange sell paths
- Treats stock anchors as pricing guidance, not hard ceilings
- Protects shulker boxes from accidental sale during the broad inventory sweep

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

- stock is allowed to rise above the configured pricing range
- selling is not hard-blocked by high stock
- sell value declines linearly between configured stock anchors
- a single sell action is priced as one aggregated batch per item key
- batch payout is computed piecewise:
  - full-price plateau before the taper range
  - linear trapezoid through the taper range
  - floor-price plateau after the taper range
- payout is rounded once at the end of the batch quote

The current canonical runtime pricing path consumes only the published, fully resolved `exchange-items.yml`.

`root-values.yml`, `stock-profiles.yml`, and `eco-envelopes.yml` remain admin/build inputs used to generate and publish that runtime catalog. They are not runtime `/shop` dependencies.

## Shulker safety rules

To protect player trust and prevent accidental storage loss:

- held shulker boxes are treated as belonging to the player holding them
- placed world containers are subject to protection / access checks
- broad sell flows do not sell the shulker container item itself
- sellcontainer only sells the contents of the supported container target

